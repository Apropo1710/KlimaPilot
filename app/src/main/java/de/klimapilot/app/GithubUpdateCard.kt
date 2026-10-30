package de.klimapilot.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch

@Composable
internal fun GithubUpdateCard(appVersion: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(GithubPrivateUpdates.isConnected(context)) }
    var pending by remember { mutableStateOf(GithubPrivateUpdates.pendingDevice(context)) }
    var busy by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf(pending?.userCode) }
    var status by rememberSaveable { mutableStateOf(if (connected) "GitHub-Verbindung gespeichert." else if (pending != null) "GitHub-Anmeldung wurde unterbrochen. Du kannst sie fortsetzen." else "Einmalig mit GitHub verbinden, um private Updates abzurufen.") }
    var newerVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var latestVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var assetId by rememberSaveable { mutableStateOf<Long?>(null) }

    suspend fun checkForUpdates() {
        busy = true
        status = "Suche nach Updates …"
        try {
            val release = GithubPrivateUpdates.latestRelease(context)
            latestVersion = release.version
            assetId = release.assetId
            val current = appVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val latest = release.version.split(".").map { it.toIntOrNull() ?: 0 }
            var comparison = 0
            for (i in 0 until maxOf(current.size, latest.size)) {
                val delta = latest.getOrElse(i) { 0 }.compareTo(current.getOrElse(i) { 0 })
                if (delta != 0) { comparison = delta; break }
            }
            newerVersion = if (comparison > 0) release.version else null
            status = if (comparison > 0) "Neue Version ${release.version} verfügbar." else "Du verwendest bereits die aktuelle Version."
        } catch (error: Exception) {
            status = "Update-Prüfung fehlgeschlagen: " + (error.message ?: error.javaClass.simpleName)
        } finally {
            busy = false
        }
    }

    LaunchedEffect(connected) {
        if (connected) checkForUpdates()
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("App-Version", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            val isCurrentVersion = status == "Du verwendest bereits die aktuelle Version."
            Text(
                status,
                color = if (isCurrentVersion) androidx.compose.ui.graphics.Color(0xFF218838) else MaterialTheme.colorScheme.onSurfaceVariant,
                style = if (isCurrentVersion) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
                fontWeight = if (isCurrentVersion) FontWeight.Bold else FontWeight.Normal
            )
            Text("Installierte Version: $appVersion", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            if (latestVersion != null) {
                Text("Verfügbare Version: $latestVersion", color = if (newerVersion != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, fontWeight = if (newerVersion != null) FontWeight.SemiBold else FontWeight.Normal)
            }
            if (code != null) {
                Text("GitHub-Code: $code", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Gib diesen Code auf der geöffneten GitHub-Seite ein und bestätige den Zugriff.", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!connected) {
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            try {
                                val device = pending ?: GithubPrivateUpdates.begin().also {
                                    GithubPrivateUpdates.savePending(context, it)
                                    pending = it
                                }
                                code = device.userCode
                                status = "GitHub im Browser öffnen und den Code bestätigen …"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(device.verificationUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                GithubPrivateUpdates.authorize(context, device)
                                connected = true
                                pending = null
                                code = null
                                status = "GitHub erfolgreich verbunden."
                            } catch (error: Exception) {
                                code = pending?.userCode
                                status = error.message ?: "GitHub-Verbindung fehlgeschlagen."
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text(if (pending != null) "Anmeldung fortsetzen" else "Mit GitHub verbinden") }
                } else {
                    OutlinedButton(enabled = !busy, onClick = { scope.launch { checkForUpdates() } }) { Text("Nach Updates suchen") }
                }
                if (newerVersion != null && assetId != null) {
                    Button(enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            status = "APK wird sicher heruntergeladen …"
                            try {
                                status = "Download abgeschlossen. APK wird geprüft …"
                                val file = GithubPrivateUpdates.download(context, assetId!!)
                                val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                                    ?: error("Die heruntergeladene Datei ist keine gültige Android-APK.")
                                if (archive.packageName != context.packageName) {
                                    error("Die APK gehört zu einer anderen App: " + archive.packageName)
                                }
                                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                                val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                                    data = uri
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                                }
                                status = "APK geprüft. Android-Installationsdialog wird geöffnet …"
                                context.startActivity(intent)
                                status = "Android wurde zum Installieren der APK aufgefordert. Falls kein Dialog erscheint, erlaube KlimaPilot unter Android > Spezieller App-Zugriff > Unbekannte Apps installieren die Installation."
                            } catch (error: Exception) {
                                status = "Update fehlgeschlagen: " + (error.message ?: error.javaClass.simpleName)
                            } finally {
                                busy = false
                            }
                        }
                    }) { Text("Update herunterladen") }
                }
            }
            if (busy) {
                Spacer(Modifier.height(2.dp))
                Text("Bitte einen Moment warten …", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}