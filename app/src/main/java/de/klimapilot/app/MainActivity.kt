package de.klimapilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AppBg = Color(0xFFF3F6FA)
private val Panel = Color(0xFFFFFFFF)
private val PanelRaised = Color(0xFFE6EDF5)
private val Blue = Color(0xFF1769AA)
private val Green = Color(0xFF16845B)
private val Muted = Color(0xFF63748A)
private val TextMain = Color(0xFF172334)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val installedAppVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "Unbekannt"
        setContent {
            val scheme = lightColorScheme(
                primary = Blue,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDCEBFA),
                onPrimaryContainer = Color(0xFF123B60),
                secondary = Green,
                onSecondary = Color.White,
                background = AppBg,
                onBackground = TextMain,
                surface = Panel,
                onSurface = TextMain,
                surfaceVariant = PanelRaised,
                onSurfaceVariant = Muted,
                outline = Color(0xFFCBD5E1)
            )
            MaterialTheme(colorScheme = scheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = AppBg) {
                    KlimaPilotApp(appVersion = installedAppVersion)
                }
            }
        }
    }
}

private class DemoRoomState(
    val roomTemperature: String,
    isOnInitial: Boolean,
    targetTempInitial: Float,
    modeInitial: String,
    fanInitial: String
) {
    var isOn by mutableStateOf(isOnInitial)
    var targetTemp by mutableFloatStateOf(targetTempInitial)
    var mode by mutableStateOf(modeInitial)
    var fan by mutableStateOf(fanInitial)
}

@Composable
private fun KlimaPilotApp(appVersion: String) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("klimapilot_settings", android.content.Context.MODE_PRIVATE) }
    var showSettings by remember { mutableStateOf(false) }
    var showManager by remember { mutableStateOf(false) }
    var showAccountSettings by remember { mutableStateOf(false) }
    var currentAccount by remember { mutableStateOf<KlimaPilotAccount?>(null) }
    var accountAuthenticated by remember { mutableStateOf(false) }
    var accountRegistry by remember { mutableStateOf<KlimaPilotAccountRegistry?>(null) }
    var accountLoading by remember { mutableStateOf(false) }
    var accountStatus by remember { mutableStateOf("") }
    var accountUsername by remember { mutableStateOf("") }
    var accountPassword by remember { mutableStateOf("") }
    var accountPasswordConfirmation by remember { mutableStateOf("") }
    var accountDisplayName by remember { mutableStateOf("") }
    val rooms = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet("rooms", null)?.toList() ?: listOf("Wohnzimmer", "Schlafzimmer"))
            if (isEmpty()) addAll(listOf("Wohnzimmer", "Schlafzimmer"))
        }
    }
    val devices = remember {
        mutableStateListOf<UserDevice>().apply {
            val saved = prefs.getString("devices", null)
            if (saved.isNullOrBlank()) {
                add(UserDevice("1", "Klimaanlage", "Wohnzimmer"))
                add(UserDevice("2", "Klimaanlage", "Schlafzimmer"))
            } else runCatching {
                val array = org.json.JSONArray(saved)
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val mappingJson = item.optJSONObject("mappings") ?: org.json.JSONObject()
                    val mappings = mutableMapOf<String, String>()
                    mappingJson.keys().forEach { key -> mappings[key] = mappingJson.optString(key) }
                    add(UserDevice(
                        item.getString("id"), item.getString("name"), item.getString("room"),
                        item.optString("adapterKey", "generic"), item.optString("adapterInstance", ""),
                        item.optString("rootObjectId", ""), mappings, item.optString("model", "")
                    ))
                }
            }
        }
    }
    fun saveData() {
        prefs.edit()
            .putStringSet("rooms", rooms.toSet())
            .putString("devices", org.json.JSONArray().apply {
                devices.forEach { device ->
                    put(org.json.JSONObject()
                        .put("id", device.id).put("name", device.name).put("room", device.room)
                        .put("adapterKey", device.adapterKey).put("adapterInstance", device.adapterInstance)
                        .put("rootObjectId", device.rootObjectId).put("mappings", org.json.JSONObject(device.mappings))
                        .put("model", device.model))
                }
            }.toString())
            .apply()
    }

    var selectedRoom by remember { mutableStateOf(rooms.firstOrNull() ?: "Wohnzimmer") }
    var selectedDeviceId by remember {
        mutableStateOf(devices.firstOrNull { it.room == selectedRoom }?.id ?: devices.firstOrNull()?.id ?: "")
    }
    val devicesInSelectedRoom = devices.filter { it.room == selectedRoom }
    val selectedDevice = devicesInSelectedRoom.firstOrNull { it.id == selectedDeviceId }
        ?: devicesInSelectedRoom.firstOrNull()
    val roomStates = remember {
        mutableStateMapOf(
            "Wohnzimmer" to DemoRoomState("25,4 °C", true, 23f, "Kühlen", "Auto"),
            "Schlafzimmer" to DemoRoomState("22,1 °C", false, 21f, "Aus", "Leise")
        )
    }
    val room = roomStates.getOrPut(selectedRoom) { DemoRoomState("—", false, 21f, "Aus", "Auto") }

    var airconConnected by remember { mutableStateOf(false) }
    val accountRepository = remember(context) { KlimaPilotAccountRepository(context) }
    val wfRacRepository = remember(context) { de.klimapilot.app.wfrac.WfRacConnectionRepository(context) }
    val startupDiscovery = remember(context) { de.klimapilot.app.wfrac.WfRacDiscovery(context) }

    DisposableEffect(startupDiscovery) {
        onDispose { startupDiscovery.stop() }
    }

    fun loadAccounts() {
        val local = accountRepository.load()
        if (local != null) {
            accountRegistry = local
            accountLoading = false
            accountStatus = ""
            val remembered = prefs.getString("klimapilotRememberedUsername", null)
            val savedAccount = remembered?.let { local.find(it) }
            if (savedAccount != null) {
                currentAccount = savedAccount
                accountAuthenticated = true
                accountUsername = savedAccount.username
            } else {
                if (!remembered.isNullOrBlank()) prefs.edit().remove("klimapilotRememberedUsername").apply()
                currentAccount = null
                accountAuthenticated = false
            }
            return
        }

        accountLoading = true
        accountStatus = "Vorhandene Konten werden einmalig aus der bisherigen ioBroker-Sicherung übernommen …"
        val legacyUrl = prefs.getString("iobroker_server", null)?.trim()?.trimEnd('/')
        if (legacyUrl.isNullOrBlank()) {
            val empty = KlimaPilotAccountRegistry()
            if (accountRepository.saveInitial(empty)) {
                accountRegistry = empty
                accountStatus = ""
            } else {
                accountStatus = "Lokale Konten konnten nicht initialisiert werden. Bitte erneut versuchen."
            }
            accountLoading = false
            return
        }
        IoBrokerClient.connect(legacyUrl, subscribeAll = false) { connected, message ->
            if (!connected) {
                IoBrokerClient.disconnect()
                accountLoading = false
                accountStatus = "Einmalige Kontoübernahme fehlgeschlagen: $message. Bitte ioBroker vorübergehend erreichbar machen und erneut versuchen. Es wurden keine Konten gelöscht."
                return@connect
            }
            IoBrokerClient.getJsonState(KlimaPilotAccountRepository.LEGACY_STATE_ID) { json, error ->
                IoBrokerClient.disconnect()
                val legacy = json?.let { KlimaPilotAccountRegistry.fromJson(it) }
                if (legacy != null) {
                    if (accountRepository.saveInitial(legacy)) {
                        accountRegistry = legacy
                        val remembered = prefs.getString("klimapilotRememberedUsername", null)
                        val savedAccount = remembered?.let { legacy.find(it) }
                        if (savedAccount != null) {
                            currentAccount = savedAccount
                            accountAuthenticated = true
                            accountUsername = savedAccount.username
                        } else {
                            currentAccount = null
                            accountAuthenticated = false
                        }
                        accountStatus = ""
                    } else {
                        accountStatus = "Die bisherigen Konten wurden gelesen, konnten aber nicht lokal gespeichert werden. Die alten Daten bleiben erhalten."
                    }
                } else if (error?.contains("existiert noch nicht", ignoreCase = true) == true) {
                    val empty = KlimaPilotAccountRegistry()
                    if (accountRepository.saveInitial(empty)) {
                        accountRegistry = empty
                        accountStatus = ""
                    } else {
                        accountStatus = "Lokale Konten konnten nicht initialisiert werden. Bitte erneut versuchen."
                    }
                } else {
                    accountStatus = "Die bisherigen Konten konnten nicht sicher übernommen werden: ${error ?: "Ungültige Kontenliste"}. Die alten Daten bleiben unverändert erhalten."
                }
                accountLoading = false
            }
        }
    }

    fun saveRegistry(expectedRevision: Long, next: KlimaPilotAccountRegistry, onSaved: (Boolean, String?) -> Unit) {
        accountRepository.save(expectedRevision, next) { saved, error ->
            if (saved) accountRegistry = next
            onSaved(saved, error)
        }
    }

    fun submitAccountLogin() {
        val registry = accountRegistry
        if (registry == null) {
            accountStatus = "Die Kontenliste ist noch nicht verfügbar. Bitte erneut laden."
            return
        }
        val password = accountPassword.toCharArray()
        try {
            if (registry.accounts.isEmpty()) {
                val next = registry.createInitialAdmin(accountUsername, accountDisplayName, password)
                accountLoading = true
                saveRegistry(registry.revision, next) { saved, error ->
                    accountLoading = false
                    if (saved) {
                        val admin = next.accounts.first()
                        currentAccount = admin
                        accountAuthenticated = true
                        prefs.edit().putString("klimapilotRememberedUsername", admin.username).apply()
                        accountPassword = ""
                        accountPasswordConfirmation = ""
                        accountStatus = ""
                    } else accountStatus = "Administrator konnte nicht angelegt werden: ${error ?: "Speichern fehlgeschlagen"}"
                }
            } else {
                val account = registry.authenticate(accountUsername, password)
                if (account == null) accountStatus = "Benutzername oder Passwort ist falsch."
                else {
                    currentAccount = account
                    accountAuthenticated = true
                    prefs.edit().putString("klimapilotRememberedUsername", account.username).apply()
                    accountPassword = ""
                    accountPasswordConfirmation = ""
                    accountStatus = ""
                }
            }
        } catch (error: Exception) {
            accountStatus = error.message ?: "Anmeldung fehlgeschlagen."
        } finally {
            password.fill('\u0000')
        }
    }

    fun addAccount(username: String, displayName: String, passwordText: String, role: String) {
        val registry = accountRegistry ?: return
        val actor = currentAccount ?: return
        val password = passwordText.toCharArray()
        try {
            val next = registry.addAccount(actor, username, displayName, password, role)
            accountLoading = true
            saveRegistry(registry.revision, next) { saved, error ->
                accountLoading = false
                accountStatus = if (saved) "" else "Benutzer konnte nicht angelegt werden: ${error ?: "Speichern fehlgeschlagen"}"
            }
        } catch (error: Exception) {
            accountStatus = error.message ?: "Benutzer konnte nicht angelegt werden."
        } finally {
            password.fill('\u0000')
        }
    }

    fun resetAccountPassword(username: String, passwordText: String) {
        val registry = accountRegistry ?: return
        val actor = currentAccount ?: return
        val password = passwordText.toCharArray()
        try {
            val next = registry.resetPassword(actor, username, password)
            accountLoading = true
            saveRegistry(registry.revision, next) { saved, error ->
                accountLoading = false
                if (saved) {
                    currentAccount = next.find(actor.username)
                    accountStatus = ""
                } else accountStatus = "Passwort konnte nicht gespeichert werden: ${error ?: "Speichern fehlgeschlagen"}"
            }
        } catch (error: Exception) {
            accountStatus = error.message ?: "Passwort konnte nicht geändert werden."
        } finally {
            password.fill('\u0000')
        }
    }

    fun deleteAccount(username: String) {
        val registry = accountRegistry ?: return
        val actor = currentAccount ?: return
        try {
            val next = registry.deleteAccount(actor, username)
            accountLoading = true
            saveRegistry(registry.revision, next) { saved, error ->
                accountLoading = false
                accountStatus = if (saved) "" else "Benutzer konnte nicht gelöscht werden: ${error ?: "Speichern fehlgeschlagen"}"
            }
        } catch (error: Exception) {
            accountStatus = error.message ?: "Benutzer konnte nicht gelöscht werden."
        }
    }

    fun updateAccountFontScale(scale: Float) {
        val registry = accountRegistry ?: return
        val actor = currentAccount ?: return
        if (kotlin.math.abs(actor.fontScale - scale) < 0.001f) return
        try {
            val next = registry.updateFontScale(actor, scale)
            accountLoading = true
            saveRegistry(registry.revision, next) { saved, error ->
                accountLoading = false
                if (saved) {
                    currentAccount = next.find(actor.username)
                    accountStatus = ""
                } else {
                    accountStatus = "Schriftgröße konnte nicht gespeichert werden: ${error ?: "Speichern fehlgeschlagen"}"
                }
            }
        } catch (error: Exception) {
            accountStatus = error.message ?: "Schriftgröße konnte nicht gespeichert werden."
        }
    }

    LaunchedEffect(Unit) { loadAccounts() }

    LaunchedEffect("wf-rac-startup-reconnect") {
        val trustedIdentities = runCatching { wfRacRepository.identities.all() }.getOrElse {
            airconConnected = false
            return@LaunchedEffect
        }
        // First-time TOFU requires an explicit user action; startup only reconnects trusted modules.
        if (trustedIdentities.isEmpty()) return@LaunchedEffect

        val savedHost = prefs.getString("wf_rac_host", null)?.trim()?.takeIf { it.isNotBlank() }
        if (savedHost != null) {
            val previousConnection = runCatching {
                withContext(Dispatchers.IO) { wfRacRepository.readStatus(savedHost) }
            }
            if (previousConnection.isSuccess) {
                airconConnected = true
                return@LaunchedEffect
            }
        } else if (wfRacRepository.identities.all().isEmpty()) {
            return@LaunchedEffect
        }

        airconConnected = false
        val discovered = mutableListOf<de.klimapilot.app.wfrac.WfRacDiscoveredService>()
        val started = startupDiscovery.start(
            onFound = { service ->
                if (discovered.none { it.host == service.host }) discovered.add(service)
            },
            onError = { }
        )
        if (started) {
            try {
                delay(10_000)
            } finally {
                startupDiscovery.stop()
            }
            for (service in discovered) {
                val verified = runCatching {
                    withContext(Dispatchers.IO) { wfRacRepository.readStatus(service.host) }
                }.isSuccess
                if (verified) {
                    airconConnected = true
                    break
                }
            }
        }
    }

    BackHandler(enabled = !accountAuthenticated) { }
    BackHandler(enabled = accountAuthenticated && (showSettings || showManager || showAccountSettings)) {
        when {
            showManager -> showManager = false
            showAccountSettings -> showAccountSettings = false
            else -> showSettings = false
        }
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            LocalDensity.current.density,
            LocalDensity.current.fontScale * (currentAccount?.fontScale ?: 1.0f)
        )
    ) {
    if (!accountAuthenticated || currentAccount == null) {
        KlimaPilotAccountLoginScreen(
            registry = accountRegistry,
            username = accountUsername, onUsername = { accountUsername = it },
            password = accountPassword, onPassword = { accountPassword = it },
            passwordConfirmation = accountPasswordConfirmation, onPasswordConfirmation = { accountPasswordConfirmation = it },
            displayName = accountDisplayName, onDisplayName = { accountDisplayName = it },
            loading = accountLoading,
            status = accountStatus,
            onReload = { loadAccounts() },
            onSubmit = { submitAccountLogin() }
        )
    } else if (showManager && currentAccount?.role == KlimaPilotAccount.ADMIN) {
        RoomDeviceManagerScreen(
            rooms, devices, ::saveData,
            { old, new ->
                if (selectedRoom == old) selectedRoom = new
                roomStates.remove(old)?.let { roomStates[new] = it }
            },
            { removed ->
                if (selectedRoom == removed) selectedRoom = rooms.firstOrNull() ?: "Wohnzimmer"
                roomStates.remove(removed)
            }
        ) { showManager = false }
    } else if (showAccountSettings) {
        KlimaPilotAccountSettingsScreen(
            account = currentAccount!!,
            accounts = accountRegistry?.accounts.orEmpty(),
            status = accountStatus,
            onBack = { showAccountSettings = false; showSettings = true },
            onSwitchAccount = {
                prefs.edit().remove("klimapilotRememberedUsername").apply()
                currentAccount = null
                accountAuthenticated = false
                accountPassword = ""
                accountPasswordConfirmation = ""
                accountStatus = ""
                showAccountSettings = false
                showSettings = false
            },
            onAddAccount = { username, displayName, password, role -> addAccount(username, displayName, password, role) },
            onResetPassword = { username, password -> resetAccountPassword(username, password) },
            onDeleteAccount = { username -> deleteAccount(username) }
        )
    } else if (showSettings) {
        SettingsScreen(
            appVersion = appVersion,
            isAdmin = currentAccount!!.role == KlimaPilotAccount.ADMIN,
            account = currentAccount!!,
            onAirconConnectionChanged = { airconConnected = it },
            onAccountSettings = { showAccountSettings = true; showSettings = false },
            onManageRoomsDevices = { showManager = true; showSettings = false },
            fontScale = currentAccount!!.fontScale,
            onFontScale = { updateAccountFontScale(it) },
            onBack = { showSettings = false }
        )
    } else {
        MainScreen(
            selectedRoom = selectedRoom,
            rooms = rooms,
            onRoomSelect = { roomName ->
                selectedRoom = roomName
                selectedDeviceId = devices.firstOrNull { it.room == roomName }?.id ?: ""
            },
            roomTemperature = room.roomTemperature,
            devicesInRoom = devicesInSelectedRoom,
            selectedDeviceId = selectedDevice?.id ?: "",
            onDeviceSelect = { deviceId -> selectedDeviceId = deviceId },
            onRoomSwipe = { direction ->
                if (rooms.size > 1) {
                    val current = rooms.indexOf(selectedRoom).coerceAtLeast(0)
                    val nextRoom = rooms[(current + direction + rooms.size) % rooms.size]
                    selectedRoom = nextRoom
                    selectedDeviceId = devices.firstOrNull { it.room == nextRoom }?.id ?: ""
                }
            },
            deviceManufacturer = if (selectedDevice != null) "Mitsubishi Heavy" else "Kein Gerät zugeordnet",
            deviceModel = selectedDevice?.model?.takeIf { it.isNotBlank() } ?: "Modell nicht hinterlegt",
            selectedDeviceName = selectedDevice?.name.orEmpty(),
            isOn = room.isOn,
            targetTemp = room.targetTemp,
            onTargetTemp = { room.targetTemp = it },
            mode = room.mode,
            onMode = { room.mode = it; room.isOn = it != "Aus" },
            fan = room.fan,
            onFan = { room.fan = it },
            airconConnected = airconConnected,
            onSettings = { showSettings = true }
        )
    }

    if (accountAuthenticated) {
        StartupUpdateCheck(
            appVersion = appVersion,
            onOpenSettings = {
                showManager = false
                showAccountSettings = false
                showSettings = true
            }
        )
    }
    }
}

@Composable
private fun StartupUpdateCheck(appVersion: String, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var availableVersion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!GithubPrivateUpdates.isConnected(context)) return@LaunchedEffect
        try {
            val release = GithubPrivateUpdates.latestRelease(context)
            val installed = appVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val latest = release.version.split(".").map { it.toIntOrNull() ?: 0 }
            var comparison = 0
            for (i in 0 until maxOf(installed.size, latest.size)) {
                val delta = latest.getOrElse(i) { 0 }.compareTo(installed.getOrElse(i) { 0 })
                if (delta != 0) { comparison = delta; break }
            }
            if (comparison > 0) availableVersion = release.version
        } catch (_: Exception) {
            // Bei fehlender Verbindung oder nicht erreichbarem GitHub kein Popup anzeigen.
        }
    }

    availableVersion?.let { version ->
        AlertDialog(
            onDismissRequest = { availableVersion = null },
            title = { Text("KlimaPilot-Update verfügbar") },
            text = {
                Text("Version $version ist verfügbar. Du kannst das Update in den Einstellungen herunterladen.")
            },
            confirmButton = {
                TextButton(onClick = {
                    availableVersion = null
                    onOpenSettings()
                }) { Text("Zu Einstellungen") }
            },
            dismissButton = {
                TextButton(onClick = { availableVersion = null }) { Text("Später") }
            }
        )
    }
}

@Composable
private fun MainScreen(
    selectedRoom: String,
    rooms: List<String>,
    onRoomSelect: (String) -> Unit,
    roomTemperature: String,
    devicesInRoom: List<UserDevice>,
    selectedDeviceId: String,
    onDeviceSelect: (String) -> Unit,
    onRoomSwipe: (Int) -> Unit,
    deviceManufacturer: String,
    deviceModel: String,
    selectedDeviceName: String,
    isOn: Boolean,
    targetTemp: Float,
    onTargetTemp: (Float) -> Unit,
    mode: String,
    onMode: (String) -> Unit,
    fan: String,
    onFan: (String) -> Unit,
    airconConnected: Boolean,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .pointerInput(rooms, selectedRoom) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var totalDrag = 0f
                    val horizontalStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        totalDrag += overSlop
                        change.consume()
                    }
                    if (horizontalStart != null) {
                        horizontalDrag(horizontalStart.id) { change ->
                            totalDrag += change.positionChange().x
                            change.consume()
                        }
                        if (rooms.size > 1 && totalDrag < -65f) onRoomSwipe(1)
                        else if (rooms.size > 1 && totalDrag > 65f) onRoomSwipe(-1)
                    }
                }
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("KLIMAPILOT", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                AnimatedContent(targetState = selectedDeviceId, transitionSpec = {
                    val forward = devicesInRoom.indexOfFirst { it.id == targetState } >= devicesInRoom.indexOfFirst { it.id == initialState }
                    if (forward) (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                    else (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                }, label = "deviceHeader") { key ->
                    Column {
                        Text(deviceManufacturer, color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(deviceModel, color = Muted, fontSize = 13.sp)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
                    Icon(if (airconConnected) Icons.Default.Cloud else Icons.Default.CloudOff, contentDescription = if (airconConnected) "Klimaanlage verbunden" else "Klimaanlage nicht verbunden", tint = if (airconConnected) Green else Muted, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Panel)) {
                    Icon(Icons.Default.Settings, contentDescription = "Einstellungen", tint = TextMain)
                }
            }
        }

        SectionTitle("RAUM AUSWÄHLEN")
        FlowModeSelector(selected = selectedRoom, options = rooms, onSelect = onRoomSelect)
        if (devicesInRoom.size > 1) {
            SectionTitle("GERÄT AUSWÄHLEN")
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                devicesInRoom.forEach { device ->
                    FilterChip(
                        selected = selectedDeviceId == device.id,
                        onClick = { onDeviceSelect(device.id) },
                        label = { Text(device.name, maxLines = 1, softWrap = false) },
                        leadingIcon = if (selectedDeviceId == device.id) ({ Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFDCEBFA),
                            selectedLabelColor = Color(0xFF123B60),
                            selectedLeadingIconColor = Blue,
                            containerColor = Color(0xFFF8FAFC),
                            labelColor = TextMain
                        ),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selectedDeviceId == device.id, borderColor = Color(0xFFCBD5E1), selectedBorderColor = Blue)
                    )
                }
            }
        }

        AnimatedContent(
            targetState = selectedDeviceId,
            transitionSpec = {
                val forward = devicesInRoom.indexOfFirst { it.id == targetState } >= devicesInRoom.indexOfFirst { it.id == initialState }
                if (forward) (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                else (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
            },
            modifier = Modifier.fillMaxWidth(),
            label = "devicePage"
        ) { deviceId ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TemperatureCard(
                    deviceName = devicesInRoom.firstOrNull { it.id == deviceId }?.name ?: devicesInRoom.firstOrNull()?.name ?: selectedRoom,
                    roomTemperature = roomTemperature,
                    isOn = isOn,
                    targetTemp = targetTemp,
                    onTargetTemp = onTargetTemp
                )

                SectionTitle("BETRIEBSMODUS")
                FlowModeSelector(selected = mode, options = listOf("Aus", "Auto", "Kühlen", "Heizen", "Entfeuchten", "Lüfter"), onSelect = onMode, enabled = false)

                SectionTitle("LÜFTERSTUFE")
                FlowModeSelector(selected = fan, options = listOf("Auto", "Leise", "1", "2", "3", "4"), onSelect = onFan, enabled = false)
                Text("Bedienung und Messwerte werden nach der direkten Modul-Anbindung aktiviert.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }

        ClimateProfilesSection(
            deviceId = selectedDeviceId,
            deviceName = selectedDeviceName
        )

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TemperatureCard(
    deviceName: String,
    roomTemperature: String,
    isOn: Boolean,
    targetTemp: Float,
    onTargetTemp: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Panel).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Muted))
            Text(
                "${deviceName.uppercase()} · STATUS NICHT VERFÜGBAR",
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("RAUMTEMPERATUR", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                Text(roomTemperature, color = TextMain, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.width(1.dp).height(48.dp).background(PanelRaised))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("SOLL", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("—", color = TextMain, fontSize = 42.sp, fontWeight = FontWeight.Light)
                }
            }
        }
        Text("Solltemperatur", color = Muted, fontSize = 13.sp)
        Slider(
            value = targetTemp,
            onValueChange = onTargetTemp,
            valueRange = 16f..30f,
            steps = 139,
            enabled = false,
            colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue, inactiveTrackColor = PanelRaised),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("16 °C", color = Muted, fontSize = 12.sp)
            Text("Nach Datenpunkt-Zuordnung verfügbar", color = Muted, fontSize = 11.sp)
            Text("30 °C", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, subtitle: String) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Panel).padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(22.dp))
        Text(title, color = Muted, fontSize = 12.sp)
        Text(value, color = TextMain, fontSize = if (value.length > 12) 15.sp else 19.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowModeSelector(selected: String, options: List<String>, onSelect: (String) -> Unit, optionLabels: Map<String, String> = emptyMap(), enabled: Boolean = true) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                enabled = enabled,
                label = { Text(optionLabels[option] ?: option) },
                leadingIcon = if (selected == option) ({ Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }) else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = if (option == "Aus") Color(0xFFFFE1E1) else Color(0xFFDCEBFA),
                    selectedLabelColor = if (option == "Aus") Color(0xFFB42318) else Color(0xFF123B60),
                    selectedLeadingIconColor = if (option == "Aus") Color(0xFFD93025) else Blue,
                    containerColor = Color(0xFFF8FAFC),
                    labelColor = TextMain
                ),
                border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected == option, borderColor = Color(0xFFCBD5E1), selectedBorderColor = if (option == "Aus") Color(0xFFD93025) else Blue)
            )
        }
    }
}


@Composable
private fun WfRacConnectionCard(onConnectionChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("klimapilot_settings", android.content.Context.MODE_PRIVATE) }
    val discovery = remember(context) { de.klimapilot.app.wfrac.WfRacDiscovery(context) }
    val connectionRepository = remember(context) { de.klimapilot.app.wfrac.WfRacConnectionRepository(context) }
    var host by remember { mutableStateOf(prefs.getString("wf_rac_host", "") ?: "") }
    var busy by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf("Noch keine Suche durchgeführt") }
    var foundModules by remember { mutableStateOf<List<de.klimapilot.app.wfrac.WfRacDiscoveredService>>(emptyList()) }
    var result by remember { mutableStateOf("Noch nicht getestet") }
    var details by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    DisposableEffect(discovery) {
        onDispose { discovery.stop() }
    }

    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Direkte Klimaanlagen-Verbindung", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("Read-only Verbindungstest zum WF-RAC-Modul. Es werden keine Steuerbefehle gesendet und kein Bedienerkonto registriert.", color = Muted, fontSize = 12.sp)
            OutlinedButton(
                enabled = !scanning && !busy,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    foundModules = emptyList()
                    scanning = true
                    scanMessage = "Suche im lokalen WLAN …"
                    val started = discovery.start(
                        onFound = { found ->
                            if (foundModules.none { it.host == found.host }) foundModules = foundModules + found
                        },
                        onError = { message ->
                            scanMessage = message
                            scanning = false
                        }
                    )
                    if (started) {
                        scope.launch {
                            delay(10_000)
                            discovery.stop()
                            scanning = false
                            scanMessage = if (foundModules.isEmpty()) {
                                "Keine WF-RAC-Module gefunden. Prüfe WLAN und Modul."
                            } else {
                                "${foundModules.size} Modul(e) gefunden"
                            }
                        }
                    } else {
                        scanning = false
                        scanMessage = "Die Suche konnte nicht gestartet werden."
                    }
                }
            ) { Text(if (scanning) "Suche läuft …" else "WLAN-Modul automatisch suchen") }
            Text(scanMessage, color = Muted, fontSize = 12.sp)
            foundModules.forEach { found ->
                OutlinedButton(
                    onClick = {
                        host = found.host
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                ) {
                    Text("${found.name} · ${found.host}")
                }
            }
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("IP-Adresse des WLAN-Moduls") },
                placeholder = { Text("z. B. 192.168.178.100") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(result, color = if (result.startsWith("Erfolgreich")) Green else Muted, fontSize = 12.sp)
            if (details.isNotBlank()) Text(details, color = TextMain, fontSize = 12.sp)
            Button(
                enabled = !busy && !scanning && host.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val address = host.trim()
                    busy = true
                    result = "Verbindung wird geprüft …"
                    onConnectionChanged(false)
                    details = ""
                    scope.launch {
                        try {
                            val connection = withContext(Dispatchers.IO) {
                                connectionRepository.readStatus(address)
                            }
                            val status = connection.status
                            val module = connection.module
                            val state = status.state
                            result = "Erfolgreich verbunden – Identität geprüft, Status nur gelesen"
                            onConnectionChanged(true)
                            details = buildString {
                                append("Aircon-ID: ").append(connection.airconId)
                                append("\nVerfahren: ").append(connection.scheme.name)
                                if (connection.scheme == de.klimapilot.app.wfrac.Scheme.HTTPS) {
                                    append("\nHTTPS-Fingerprint: ").append(connection.certificatePin ?: "fehlt")
                                }
                                append("\nGeräteidentität dauerhaft gespeichert")
                                append("\nWLAN-Firmware: ").append(module?.wireless ?: "nicht gemeldet")
                                append("\nMCU-Firmware: ").append(module?.mcu ?: "nicht gemeldet")
                                append("\nModultyp: ").append(module?.firmType ?: "nicht gemeldet")
                                append("\nBelegte Konten: ").append(status.accounts?.toString() ?: "unbekannt").append(" / 4")
                                append("\nGerätestatus: ").append(if (state.power) "Ein" else "Aus")
                                append("\nBetriebsmodus: ").append(state.mode?.name ?: "unbekannt")
                                append("\nSolltemperatur: ").append(state.presetTemp).append(" °C")
                                append("\nLüfterstufe: ").append(state.airFlow)
                                append("\nRaumtemperatur: ").append(state.indoorTemp?.let { "$it °C" } ?: "nicht gemeldet")
                                append("\nAußentemperatur: ").append(state.outdoorTemp?.let { "$it °C" } ?: "nicht gemeldet")
                                append("\nFehlercode: ").append(state.errorCode ?: "keiner gemeldet")
                                if (status.lockedForS > 0) append("\nSchreibsperre eines anderen Clients: ").append(status.lockedForS).append(" s")
                            }
                        } catch (error: Exception) {
                            onConnectionChanged(false)
                            result = if (error is SecurityException) "Sicherheitsprüfung fehlgeschlagen" else "Verbindung fehlgeschlagen"
                            details = error.message ?: error.javaClass.simpleName
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "Prüfe Verbindung …" else "WLAN-Modul auslesen") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)

@Composable
private fun IoBrokerConnectionSettingsCard() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("klimapilot_settings", android.content.Context.MODE_PRIVATE) }
    fun initialAddress(): String {
        val configured = prefs.getString("klimapilot_iobroker_address", null)
        if (!configured.isNullOrBlank()) return configured
        val legacy = prefs.getString("iobroker_server", null)?.trim().orEmpty()
        if (legacy.startsWith("ws://") || legacy.startsWith("wss://")) {
            return legacy.substringAfter("://").substringBefore("/").substringBefore("?").substringBefore(":")
        }
        return "192.168.178.54"
    }
    fun initialPort(): String {
        val configured = prefs.getString("klimapilot_iobroker_port", null)
        if (!configured.isNullOrBlank()) return configured
        val legacy = prefs.getString("iobroker_server", null)?.trim().orEmpty()
        val authority = legacy.substringAfter("://", "")
        val explicit = authority.substringBefore("/").substringAfterLast(":", "")
        return explicit.takeIf { it.isNotBlank() } ?: "8084"
    }
    fun initialSecure(): Boolean {
        val saved = prefs.getString("klimapilot_iobroker_protocol", null)
        if (saved != null) return saved == "wss"
        return prefs.getString("iobroker_server", null)?.trim()?.startsWith("wss://") == true
    }
    var address by remember { mutableStateOf(initialAddress()) }
    var port by remember { mutableStateOf(initialPort()) }
    var secure by remember { mutableStateOf(initialSecure()) }
    var status by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    val valid = address.trim().isNotBlank() && !address.contains("://") && !address.contains("/") &&
        port.toIntOrNull()?.let { it in 1..65535 } == true
    val url = (if (secure) "wss://" else "ws://") + address.trim() + ":" + port.toIntOrNull().coerceAtLeast(1)
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ioBroker-Verbindung", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Diese Verbindung wird für die Synchronisierung deiner Klimaprofile verwendet.", color = Muted, fontSize = 12.sp)
            OutlinedTextField(value = address, onValueChange = { address = it.trim() }, label = { Text("IP-Adresse oder Hostname") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = port.toIntOrNull()?.let { it !in 1..65535 } ?: true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !secure, onClick = { secure = false }, label = { Text("ws://") })
                FilterChip(selected = secure, onClick = { secure = true }, label = { Text("wss://") })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    if (!valid) { status = "Bitte IP-Adresse und einen gültigen Port eingeben."; return@OutlinedButton }
                    prefs.edit().putString("klimapilot_iobroker_address", address.trim())
                        .putString("klimapilot_iobroker_port", port)
                        .putString("klimapilot_iobroker_protocol", if (secure) "wss" else "ws")
                        .putString("iobroker_server", url).apply()
                    status = "Verbindungseinstellungen gespeichert."
                }, enabled = valid, modifier = Modifier.weight(1f)) { Text("Speichern") }
                Button(onClick = {
                    if (!valid) { status = "Bitte IP-Adresse und einen gültigen Port eingeben."; return@Button }
                    prefs.edit().putString("klimapilot_iobroker_address", address.trim())
                        .putString("klimapilot_iobroker_port", port)
                        .putString("klimapilot_iobroker_protocol", if (secure) "wss" else "ws")
                        .putString("iobroker_server", url).apply()
                    testing = true; status = "Verbindung wird geprüft …"
                    IoBrokerClient.connect(url, subscribeAll = false) { connected, message ->
                        IoBrokerClient.disconnect()
                        testing = false
                        status = if (connected) "Verbindung erfolgreich: $message" else "Verbindung fehlgeschlagen: $message"
                    }
                }, enabled = valid && !testing, modifier = Modifier.weight(1f)) { Text(if (testing) "Prüfe …" else "Testen") }
            }
            if (status.isNotBlank()) Text(status, color = if (status.contains("fehlgeschlagen") || status.contains("Bitte")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsScreen(
    appVersion: String,
    isAdmin: Boolean,
    onAirconConnectionChanged: (Boolean) -> Unit,
    account: KlimaPilotAccount,
    onAccountSettings: () -> Unit,
    onManageRoomsDevices: () -> Unit,
    fontScale: Float,
    onFontScale: (Float) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .background(AppBg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(AppBg)
                .padding(horizontal = 20.dp).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = TextMain) }
            Column {
                Text("Einstellungen", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Konto und App", color = Muted, fontSize = 12.sp)
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Angemeldet als", color = Muted, fontSize = 12.sp)
                Text(account.displayName, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${account.username} · ${if (isAdmin) "Administrator" else "Benutzer"}", color = Muted, fontSize = 12.sp)
                OutlinedButton(onClick = onAccountSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Benutzerkonto und Benutzerwechsel")
                }
            }
        }
        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Schriftgröße", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Wird lokal in deinem Benutzerkonto auf diesem Gerät gespeichert.", color = Muted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(0.85f to "Klein", 1.0f to "Normal", 1.15f to "Groß", 1.30f to "Sehr groß", 1.50f to "Max.").forEach { (scale, label) ->
                        val selected = kotlin.math.abs(fontScale - scale) < 0.01f
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { onFontScale(scale) }
                                .padding(vertical = 8.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else TextMain, textAlign = TextAlign.Center)
                            Text("${(scale * 100).toInt()}%", fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else TextMain)
                        }
                    }
                }
            }
        }
        if (isAdmin) {
            IoBrokerConnectionSettingsCard()
            WfRacConnectionCard(onConnectionChanged = onAirconConnectionChanged)
            OutlinedButton(
                onClick = onManageRoomsDevices,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Räume & Geräte verwalten")
            }
        } else {
            Card(shape = RoundedCornerShape(16.dp)) {
                Text(
                    "Verbindungs-, Raum- und Gerätekonfiguration kann nur ein Administrator ändern.",
                    modifier = Modifier.padding(14.dp), color = Muted, fontSize = 12.sp
                )
            }
        }
        GithubUpdateCard(appVersion)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Zurück zur Klimaübersicht")
        }
        }
    }
}
