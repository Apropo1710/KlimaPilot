package de.klimapilot.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** The kinds of Android notifications; each can be switched off in the settings. */
internal enum class HintKind(val pref: String, val label: String) {
    FILTER("notify_filter", "Filter fällig"),
    SCHEDULE("notify_schedule", "Zeitplan fehlgeschlagen"),
    OFFLINE("notify_offline", "Anlage länger nicht erreichbar"),
}

/**
 * Occasional background check (about every 2 hours, only on Wi-Fi, batched by Android): asks
 * ioBroker for due filters, failed schedules and units that have not been reachable for over an
 * hour, and shows each finding once as a notification. No connection is kept open.
 */
internal object KlimaNotifications {
    private const val CHANNEL = "hints"
    private const val WORK = "klimapilot-hints"
    private const val PREFS = "klimapilot_settings"
    private const val PREF_SENT = "notify_sent_keys"
    private const val PREF_ASKED = "notify_permission_asked"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Off until switched on in the settings. */
    fun enabled(context: Context, kind: HintKind) = prefs(context).getBoolean(kind.pref, false)
    fun anyEnabled(context: Context) = HintKind.entries.any { enabled(context, it) }

    fun setEnabled(context: Context, kind: HintKind, value: Boolean) {
        prefs(context).edit().putBoolean(kind.pref, value).apply()
        schedule(context)
    }

    fun permissionNeeded(context: Context) = Build.VERSION.SDK_INT >= 33 &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    /** Ask for the permission only once on start; later it can be granted from the settings card. */
    fun shouldAskOnStart(context: Context) = anyEnabled(context) && permissionNeeded(context) && !prefs(context).getBoolean(PREF_ASKED, false)
    fun markAsked(context: Context) = prefs(context).edit().putBoolean(PREF_ASKED, true).apply()

    /** Starts or stops the periodic check depending on the switches. */
    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        if (!anyEnabled(context)) { work.cancelUniqueWork(WORK); return }
        val request = PeriodicWorkRequestBuilder<HintWorker>(2, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Hinweise zu den Klimaanlagen", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    /** Shows the notification unless this finding (key) was already reported. */
    fun notifyOnce(context: Context, key: String, title: String, text: String) {
        val prefs = prefs(context)
        val sent = prefs.getStringSet(PREF_SENT, emptySet()).orEmpty()
        if (key in sent || permissionNeeded(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_klimapilot)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(key.hashCode(), notification) }
        // Keep the list of reported findings short; old keys are never seen again anyway.
        prefs.edit().putStringSet(PREF_SENT, (sent + key).toList().takeLast(200).toSet()).apply()
    }
}

/** What the check needs from ioBroker. */
internal data class HintSnapshot(
    val units: List<EnergyUnitData>,
    val filter: FilterConfig,
    val schedules: List<ClimateSchedule>,
    val status: Map<String, ScheduleRun>,
)

internal class HintWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!KlimaNotifications.anyEnabled(context) || KlimaNotifications.permissionNeeded(context)) return Result.success()
        val address = context.getSharedPreferences("klimapilot_settings", Context.MODE_PRIVATE).getString("iobroker_server", null).orEmpty()
        if (address.isBlank()) return Result.success()
        // Not at home or ioBroker busy: simply try again at the next run.
        val snapshot = withTimeoutOrNull(25_000) { fetch(address) } ?: return Result.success()
        HintRules.findings(snapshot, System.currentTimeMillis()).forEach { finding ->
            if (KlimaNotifications.enabled(context, finding.kind)) KlimaNotifications.notifyOnce(context, finding.key, finding.title, finding.text)
        }
        return Result.success()
    }

    private suspend fun fetch(address: String): HintSnapshot? = suspendCancellableCoroutine { cont ->
        val url = address.trim().trimEnd('/')
        if (!url.startsWith("ws://") && !url.startsWith("wss://")) { cont.resume(null); return@suspendCancellableCoroutine }
        val socket = IoBrokerLease(url)
        val done = AtomicBoolean(false)
        fun finish(result: HintSnapshot?) {
            if (!done.compareAndSet(false, true)) return
            runCatching { socket.off() }
            runCatching { socket.disconnect() }
            cont.resume(result)
        }
        cont.invokeOnCancellation { if (done.compareAndSet(false, true)) { runCatching { socket.off() }; runCatching { socket.disconnect() } } }
        fun json(args: Array<Any?>): JSONObject? =
            ((args.getOrNull(1) as? JSONObject)?.opt("val") as? String)?.let { runCatching { JSONObject(it) }.getOrNull() }
        socket.on("connect") {
            socket.emit("authenticate", IoBrokerAck { auth ->
                if (auth.firstOrNull() as? Boolean != true) { finish(null); return@IoBrokerAck }
                socket.emit("getStates", "${EnergyIoBroker.ROOT}.units.*", IoBrokerAck { statesArgs ->
                    val states = statesArgs.getOrNull(1) as? JSONObject ?: JSONObject()
                    val units = states.keys().asSequence().mapNotNull { id ->
                        (states.optJSONObject(id)?.opt("val") as? String)?.let { runCatching { JSONObject(it) }.getOrNull() }?.let(EnergyMath::parseUnit)
                    }.toList()
                    socket.emit("getState", FilterIoBroker.CONFIG_ID, IoBrokerAck { filterArgs ->
                        socket.emit("getState", ScheduleIoBroker.CONFIG_ID, IoBrokerAck { configArgs ->
                            socket.emit("getState", ScheduleIoBroker.STATUS_ID, IoBrokerAck { statusArgs ->
                                finish(HintSnapshot(
                                    units = units,
                                    filter = FilterConfig.fromJson(json(filterArgs)),
                                    schedules = ScheduleJson.configFromJson(json(configArgs)).let { (enabled, items) -> if (enabled) items else emptyList() },
                                    status = ScheduleJson.statusFromJson(json(statusArgs)),
                                ))
                            })
                        })
                    })
                })
            })
        }
        socket.on("connect_error") { finish(null) }
        socket.connect()
    }
}

internal data class HintFinding(val kind: HintKind, val key: String, val title: String, val text: String)

/** Decides what is worth a notification; pure, so it can be tested. */
internal object HintRules {
    private const val OFFLINE_AFTER_MS = 60 * 60_000L
    /** Weekly schedules are retried for 10 minutes; only then a failure is final. */
    private const val RETRY_WINDOW_MS = 11 * 60_000L

    fun findings(s: HintSnapshot, now: Long): List<HintFinding> {
        val result = mutableListOf<HintFinding>()
        val names = s.units.associate { it.airconId to it.room.ifBlank { it.name } }
        s.units.forEach { unit ->
            val room = names[unit.airconId].orEmpty()
            s.filter.dueFilters(unit).forEach { (kind, days, _) ->
                val baseline = unit.runDays - days
                result += HintFinding(
                    HintKind.FILTER, "filter:${unit.airconId}:${kind.name}:$baseline",
                    kind.reminder, "$room: $days Betriebstage seit der letzten Reinigung. In der App mit „Erledigt“ bestätigen."
                )
            }
            val lastOk = unit.lastPollAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            if (unit.lastError != null && lastOk != null && now - lastOk > OFFLINE_AFTER_MS) {
                val since = Instant.ofEpochMilli(lastOk).atZone(ZoneId.systemDefault())
                result += HintFinding(
                    HintKind.OFFLINE, "offline:${unit.airconId}:${unit.lastPollAt}",
                    "$room nicht erreichbar",
                    "Die Klimaanlage $room ist seit %02d:%02d Uhr im Heimnetz nicht erreichbar.".format(since.hour, since.minute)
                )
            }
        }
        s.schedules.filter { it.enabled || it.kind == "once" }.forEach { item ->
            val run = s.status[item.id] ?: return@forEach
            val at = run.at?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@forEach
            if (run.done || run.result == null || run.result == "ok" || now - at < RETRY_WINDOW_MS) return@forEach
            val room = names[item.airconId] ?: "Klimaanlage"
            val label = if (item.quick) "Schnell-Timer" else "Zeitplan „${ScheduleMath.scheduleLabel(item, LocalDateTime.now())}“"
            result += HintFinding(
                HintKind.SCHEDULE, "schedule:${item.id}:${run.key}",
                "Zeitplan fehlgeschlagen",
                "$label für $room konnte nicht ausgeführt werden: ${run.result}"
            )
        }
        return result
    }
}

/** Settings card: one switch per kind, permission request and an ⓘ. */
@Composable
internal fun NotificationSettingsCard(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    var states by remember { mutableStateOf(HintKind.entries.associateWith { KlimaNotifications.enabled(context, it) }) }
    var permissionMissing by remember { mutableStateOf(KlimaNotifications.permissionNeeded(context)) }
    var showInfo by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionMissing = KlimaNotifications.permissionNeeded(context)
    }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3EEF7))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().clickable { onExpandedChange(!expanded) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Benachrichtigungen", color = SText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        when {
                            states.values.none { it } -> "Aus"
                            permissionMissing -> "Android erlaubt noch keine Benachrichtigungen"
                            else -> HintKind.entries.filter { states[it] == true }.joinToString(" · ") { it.label }
                        },
                        color = SMuted, fontSize = 11.sp
                    )
                }
                if (expanded) Box(Modifier.padding(end = 8.dp).size(26.dp).clip(CircleShape).clickable { showInfo = true }, contentAlignment = Alignment.Center) {
                    Text("ⓘ", color = SMuted, fontSize = 15.sp)
                }
                Text(if (expanded) "⌃" else "⌄", color = SMuted, fontSize = 20.sp)
            }
            if (expanded) {
                HintKind.entries.forEach { kind ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(kind.label, color = SText, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Switch(states[kind] == true, { value ->
                            KlimaNotifications.setEnabled(context, kind, value)
                            states = states + (kind to value)
                            if (value && Build.VERSION.SDK_INT >= 33 && KlimaNotifications.permissionNeeded(context)) {
                                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }, colors = blueSwitchColors())
                    }
                }
                if (permissionMissing && states.values.any { it } && Build.VERSION.SDK_INT >= 33) {
                    TextButton(onClick = { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Benachrichtigungen erlauben")
                    }
                }
            }
        }
    }
    if (showInfo) {
        KlimaPilotAlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
            title = { Text("Benachrichtigungen") },
            text = {
                Text(
                    "Etwa alle zwei Stunden fragt die App im Hintergrund kurz bei ioBroker nach – nur im WLAN, gebündelt von Android, " +
                        "ohne dauerhafte Verbindung. Jeder Hinweis kommt einmal. Unterwegs ist ioBroker nicht erreichbar; " +
                        "die Prüfung holt das zu Hause nach. Eine Anlage gilt als nicht erreichbar, wenn ioBroker sie seit über einer Stunde nicht abfragen konnte."
                )
            }
        )
    }
}
