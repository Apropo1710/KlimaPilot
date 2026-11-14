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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
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
    val accountRepository = remember { KlimaPilotAccountRepository(IoBrokerClient) }

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

    val initialServerUrl = remember {
        val saved = prefs.getString("iobroker_server", null)?.trim()?.trimEnd('/')
        when {
            saved.isNullOrBlank() -> "ws://192.168.178.54:8084"
            saved.startsWith("ws://") || saved.startsWith("wss://") -> saved
            saved.startsWith("http://") || saved.startsWith("https://") -> {
                val host = saved.substringAfter("://").substringBeforeLast(":")
                "ws://$host:8084"
            }
            else -> "ws://192.168.178.54:8084"
        }
    }
    var serverUrl by remember { mutableStateOf(initialServerUrl) }
    var ioBrokerConnected by remember { mutableStateOf(false) }
    var ioBrokerChecking by remember { mutableStateOf(false) }
    var ioBrokerStatus by remember { mutableStateOf("Noch nicht verbunden") }

    fun loadAccounts() {
        if (!IoBrokerClient.isConnected()) {
            accountRegistry = null
            accountLoading = false
            accountStatus = "ioBroker ist noch nicht vollständig verbunden. Die Anmeldung bleibt gesperrt."
            return
        }
        accountLoading = true
        accountStatus = ""
        accountRepository.load { registry, error ->
            accountLoading = false
            accountRegistry = registry
            if (error != null || registry == null) {
                accountStatus = "KlimaPilot-Konten konnten nicht geladen werden: ${error ?: "Unbekannter Fehler"}"
                return@load
            }
            val rememberedUsername = prefs.getString("klimapilotRememberedUsername", null)
            val activeUsername = currentAccount?.username ?: rememberedUsername
            val rememberedAccount = activeUsername?.let { registry.find(it) }
            if (rememberedAccount != null) {
                currentAccount = rememberedAccount
                accountAuthenticated = true
                accountUsername = rememberedAccount.username
            } else {
                if (!rememberedUsername.isNullOrBlank()) {
                    prefs.edit().remove("klimapilotRememberedUsername").apply()
                }
                currentAccount = null
                accountAuthenticated = false
            }
        }
    }

    fun saveRegistry(expectedRevision: Long, next: KlimaPilotAccountRegistry, onSaved: (Boolean, String?) -> Unit) {
        accountRepository.save(expectedRevision, next) { saved, error ->
            if (saved) accountRegistry = next
            onSaved(saved, error)
        }
    }

    fun connectToIoBroker() {
        val target = serverUrl.trim().trimEnd('/')
        if (!(target.startsWith("ws://") || target.startsWith("wss://")) || target.contains("xxx")) {
            ioBrokerConnected = false
            ioBrokerStatus = "Bitte eine gültige Serveradresse eingeben."
            accountStatus = ioBrokerStatus
            return
        }
        ioBrokerChecking = true
        ioBrokerConnected = false
        ioBrokerStatus = "Verbindung wird aufgebaut …"
        prefs.edit().putString("iobroker_server", target).apply()
        IoBrokerClient.connect(target) { ok, message ->
            ioBrokerConnected = ok
            ioBrokerChecking = false
            ioBrokerStatus = message
            if (ok) loadAccounts()
            else {
                accountRegistry = null
                accountStatus = "ioBroker-Verbindung fehlgeschlagen: $message"
            }
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

    LaunchedEffect(Unit) {
        if (serverUrl.isNotBlank() && !serverUrl.contains("xxx")) connectToIoBroker()
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
            loading = accountLoading || ioBrokerChecking,
            connected = ioBrokerConnected,
            status = accountStatus,
            onRetryConnection = { connectToIoBroker() },
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
            serverUrl = serverUrl,
            onServerUrlChange = { serverUrl = it },
            onServerUrlConfirm = { prefs.edit().putString("iobroker_server", serverUrl.trim().trimEnd('/')).apply() },
            connected = ioBrokerConnected,
            checking = ioBrokerChecking,
            status = ioBrokerStatus,
            onConnect = { connectToIoBroker() },
            appVersion = appVersion,
            isAdmin = currentAccount!!.role == KlimaPilotAccount.ADMIN,
            account = currentAccount!!,
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
            deviceManufacturer = selectedDevice?.let { device ->
                AdapterProfiles.all.firstOrNull { it.key == device.adapterKey }?.title?.takeUnless { it.startsWith("Universell") }
                    ?: "Klimaanlage"
            } ?: "Kein Gerät zugeordnet",
            deviceModel = selectedDevice?.model?.takeIf { it.isNotBlank() } ?: "Modell nicht hinterlegt",
            isOn = room.isOn,
            targetTemp = room.targetTemp,
            onTargetTemp = { room.targetTemp = it },
            mode = room.mode,
            onMode = { room.mode = it; room.isOn = it != "Aus" },
            fan = room.fan,
            onFan = { room.fan = it },
            ioBrokerConnected = ioBrokerConnected,
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
    isOn: Boolean,
    targetTemp: Float,
    onTargetTemp: (Float) -> Unit,
    mode: String,
    onMode: (String) -> Unit,
    fan: String,
    onFan: (String) -> Unit,
    ioBrokerConnected: Boolean,
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
                    // Neutral until a real ioBroker connection can confirm success.
                    Icon(if (ioBrokerConnected) Icons.Default.Cloud else Icons.Default.CloudOff, contentDescription = if (ioBrokerConnected) "ioBroker verbunden" else "ioBroker nicht verbunden", tint = if (ioBrokerConnected) Green else Muted, modifier = Modifier.size(19.dp))
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
                Text("Bedienung und Messwerte werden nach Zuordnung der ioBroker-Datenpunkte aktiviert.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onServerUrlConfirm: () -> Unit,
    connected: Boolean,
    checking: Boolean,
    status: String,
    onConnect: () -> Unit,
    appVersion: String,
    isAdmin: Boolean,
    account: KlimaPilotAccount,
    onAccountSettings: () -> Unit,
    onManageRoomsDevices: () -> Unit,
    fontScale: Float,
    onFontScale: (Float) -> Unit,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = TextMain) }
            Column {
                Text("Einstellungen", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Konto und App", color = Muted, fontSize = 12.sp)
            }
        }
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
                Text("Wird in deinem Benutzerkonto gespeichert und auf anderen Geräten übernommen.", color = Muted, fontSize = 12.sp)
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
            OutlinedButton(
                onClick = onManageRoomsDevices,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Räume & Geräte verwalten")
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFDCEAF2)), shape = RoundedCornerShape(18.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF398FBA)), contentAlignment = Alignment.Center) {
                            Icon(if (connected) Icons.Default.Cloud else Icons.Default.CloudOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("ioBroker-Verbindung", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(if (connected) "Verbunden" else if (checking) "Verbindung wird aufgebaut …" else "Nicht verbunden",
                                color = if (connected) Green else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = onServerUrlChange,
                        label = { Text("WebSocket-Adresse") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                onServerUrlConfirm()
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }) { Icon(Icons.Default.Edit, contentDescription = "Adresse übernehmen", tint = Blue) }
                        }
                    )
                    Text(
                        if (connected) "Verbindung hergestellt." else if (checking) "Verbindung wird aufgebaut …"
                        else "Keine Verbindung – bitte WLAN/VPN und ioBroker prüfen.",
                        color = if (connected) Green else if (checking) Muted else Color(0xFFD32F2F),
                        fontSize = 12.sp
                    )
                    if (!connected) {
                        Button(onClick = {
                            onServerUrlConfirm()
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onConnect()
                        }, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                            Text(if (checking) "Verbindung wird aufgebaut …" else "Verbindung herstellen")
                        }
                    } else {
                        Text("Live-Daten und Steuerung laufen über diese Verbindung.", color = Muted, fontSize = 11.sp)
                    }
                }
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
