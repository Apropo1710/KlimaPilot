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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import de.klimapilot.app.wfrac.AirconState
import de.klimapilot.app.wfrac.Mode
import de.klimapilot.app.wfrac.Codec
import de.klimapilot.app.sync.ClimateProfilePeerDiscovery

private val AppBg = Color(0xFFF3F6FA)
private val Panel = Color(0xFFFFFFFF)
private val PanelRaised = Color(0xFFE3EEF7)
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
                primaryContainer = Color(0xFFE3EEF7),
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

@Composable
private fun KlimaPilotApp(appVersion: String) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("klimapilot_settings", android.content.Context.MODE_PRIVATE) }
    var showSettings by remember { mutableStateOf(false) }
    var showManager by remember { mutableStateOf(false) }
    var showProfileManager by remember { mutableStateOf(false) }
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
                        item.optString("rootObjectId", ""), mappings, item.optString("model", ""), item.optString("syncDeviceKey", ""),
                        item.optString("wfRacHost", ""), item.optString("wfRacAirconId", ""), item.optBoolean("demoMode", false)
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
                        .put("model", device.model).put("syncDeviceKey", device.syncDeviceKey)
                        .put("wfRacHost", device.wfRacHost).put("wfRacAirconId", device.wfRacAirconId)
                        .put("demoMode", device.demoMode))
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
    val controller = remember(context) { ClimateDeviceController(context) }
    var currentAirconState by remember(selectedDevice?.id) { mutableStateOf<AirconState?>(null) }
    var airconConnected by remember(selectedDevice?.id) { mutableStateOf(false) }
    var controlBusy by remember(selectedDevice?.id) { mutableStateOf(false) }
    var controlStatus by remember(selectedDevice?.id) { mutableStateOf("") }
    val controlScope = rememberCoroutineScope()
    val accountRepository = remember(context) { KlimaPilotAccountRepository(context) }
    LaunchedEffect(selectedDevice?.id, selectedDevice?.wfRacAirconId, selectedDevice?.demoMode, showSettings, showManager, showProfileManager, showAccountSettings) {
        currentAirconState = null
        airconConnected = false
        controlStatus = ""
        if (showSettings || showManager || showProfileManager || showAccountSettings) return@LaunchedEffect
        val device = selectedDevice ?: return@LaunchedEffect
        if (!device.demoMode && device.wfRacAirconId.isBlank()) return@LaunchedEffect
        while (true) {
            runCatching { withContext(Dispatchers.IO) { controller.read(device) } }
                .onSuccess { state -> currentAirconState = state; airconConnected = true; controlStatus = "" }
                .onFailure { error -> airconConnected = false; controlStatus = error.message ?: "Gerätestatus konnte nicht gelesen werden." }
            delay(if (device.demoMode) 3_000L else 15_000L)
        }
    }

    fun applyDeviceChange(
        onComplete: ((Boolean, String?) -> Unit)? = null,
        change: (AirconState) -> AirconState
    ) {
        val device = selectedDevice
        if (device == null) {
            onComplete?.invoke(false, "Kein Gerät ausgewählt.")
            return
        }
        if (controlBusy) {
            onComplete?.invoke(false, "Es läuft bereits ein Steuerbefehl.")
            return
        }
        if (!device.demoMode && device.wfRacAirconId.isBlank()) {
            val message = "Bitte zuerst in den Geräteeinstellungen das WLAN-Modul koppeln."
            controlStatus = message
            onComplete?.invoke(false, message)
            return
        }
        controlScope.launch {
            controlBusy = true
            controlStatus = "Befehl wird übertragen …"
            try {
                val updated = withContext(Dispatchers.IO) { controller.apply(device, change) }
                currentAirconState = updated
                airconConnected = true
                controlStatus = "Einstellung übernommen."
                onComplete?.invoke(true, controlStatus)
            } catch (error: Exception) {
                val message = error.message ?: "Steuerbefehl fehlgeschlagen."
                controlStatus = message
                onComplete?.invoke(false, message)
            } finally {
                controlBusy = false
            }
        }
    }

    fun loadAccounts() {
        when (val local = accountRepository.load()) {
            is KlimaPilotAccountLoadResult.Loaded -> {
                val registry = local.registry
                accountRegistry = registry
                accountLoading = false
                accountStatus = ""
                val remembered = prefs.getString("klimapilotRememberedUsername", null)
                val savedAccount = remembered?.let { registry.find(it) }
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
            is KlimaPilotAccountLoadResult.Invalid -> {
                accountRegistry = null
                accountLoading = false
                currentAccount = null
                accountAuthenticated = false
                accountStatus = if (local.backupSaved) {
                    "Die lokal gespeicherten Kontendaten sind beschädigt oder nicht lesbar. Die unveränderten Rohdaten wurden intern gesichert. Es wurden keine Konten überschrieben. Bitte sichere das Gerät, bevor du weitere Schritte unternimmst."
                } else {
                    "Die lokal gespeicherten Kontendaten sind beschädigt oder nicht lesbar. Eine zusätzliche Sicherung war nicht möglich. Die vorhandenen Daten wurden nicht verändert."
                }
                return
            }
            KlimaPilotAccountLoadResult.Missing -> Unit
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

    BackHandler(enabled = !accountAuthenticated) { }
    BackHandler(enabled = accountAuthenticated && (showSettings || showManager || showProfileManager || showAccountSettings)) {
        when {
            showManager -> { showManager = false; showSettings = true }
            showProfileManager -> { showProfileManager = false; showSettings = true }
            showAccountSettings -> { showAccountSettings = false; showSettings = true }
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
            rooms = rooms,
            devices = devices,
            save = ::saveData,
            rename = { old, new ->
                if (selectedRoom == old) selectedRoom = new
            },
            onRemoveRoom = { removed ->
                if (selectedRoom == removed) selectedRoom = rooms.firstOrNull() ?: "Wohnzimmer"
            },
            onDeleteDevice = { device ->
                if (device.wfRacAirconId.isBlank()) null
                else try {
                    if (controller.forget(device.wfRacAirconId)) null
                    else "Die gespeicherte Kopplung konnte nicht entfernt werden. Das Gerät wurde nicht gelöscht."
                } catch (error: Exception) {
                    error.message ?: "Die Kopplung konnte nicht sicher zurückgesetzt werden. Das Gerät wurde nicht gelöscht."
                }
            },
            onResetPairing = { airconId ->
                try {
                    if (controller.forget(airconId)) null
                    else "Die gespeicherte Kopplung konnte nicht entfernt werden."
                } catch (error: Exception) {
                    error.message ?: "Die Kopplung konnte nicht sicher zurückgesetzt werden."
                }
            },
            back = { showManager = false; showSettings = true }
        )
    } else if (showProfileManager && currentAccount?.role == KlimaPilotAccount.ADMIN) {
        ClimateProfileManagerScreen(devices = devices) { showProfileManager = false; showSettings = true }
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
            onManageProfiles = { showProfileManager = true; showSettings = false },
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
            devicesInRoom = devicesInSelectedRoom,
            allDevices = devices,
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
            selectedDeviceSyncKey = selectedDevice?.syncDeviceKey.orEmpty(),
            airconState = currentAirconState,
            controlBusy = controlBusy,
            controlStatus = controlStatus,
            onPower = { enabled -> applyDeviceChange { it.copy(power = enabled) } },
            onTargetTemp = { value -> applyDeviceChange { it.copy(presetTemp = value.toDouble()) } },
            onMode = { selected ->
                val mapped = when (selected) {
                    "Auto" -> Mode.AUTO; "Kühlen" -> Mode.COOL; "Heizen" -> Mode.HEAT
                    "Entfeuchten" -> Mode.DRY; else -> Mode.FAN
                }
                applyDeviceChange { it.copy(power = true, mode = mapped) }
            },
            onFan = { selected ->
                val mapped = when (selected) { "Auto" -> 0; "Leise" -> 1; "Niedrig" -> 2; "Mittel" -> 3; else -> 4 }
                applyDeviceChange { it.copy(airFlow = mapped) }
            },
            onVerticalSwing = { value -> applyDeviceChange { it.copy(windUD = value) } },
            onHorizontalSwing = { value -> applyDeviceChange { it.copy(windLR = value) } },
            onThreeDAuto = { enabled -> applyDeviceChange { it.copy(entrust = enabled) } },
            onApplyProfile = { profile, onComplete ->
                applyDeviceChange(
                    change = { current ->
                        val supportsVaneLR = Codec.modelHasVaneLR(current.modelNrRaw) == true
                        current.copy(
                            power = profile.power ?: current.power,
                            mode = profile.mode?.let { Mode.valueOf(it) } ?: current.mode,
                            presetTemp = profile.targetTemp ?: current.presetTemp,
                            airFlow = profile.fanSpeed ?: current.airFlow,
                            windUD = profile.verticalSwing ?: current.windUD,
                            windLR = if (supportsVaneLR) profile.horizontalSwing ?: current.windLR else current.windLR,
                            entrust = if (supportsVaneLR) profile.threeDAuto ?: current.entrust else current.entrust
                        )
                    },
                    onComplete = onComplete
                )
            },
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
        KlimaPilotAlertDialog(
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
    devicesInRoom: List<UserDevice>,
    allDevices: List<UserDevice>,
    selectedDeviceId: String,
    onDeviceSelect: (String) -> Unit,
    onRoomSwipe: (Int) -> Unit,
    deviceManufacturer: String,
    deviceModel: String,
    selectedDeviceName: String,
    selectedDeviceSyncKey: String,
    airconState: AirconState?,
    controlBusy: Boolean,
    controlStatus: String,
    onPower: (Boolean) -> Unit,
    onTargetTemp: (Float) -> Unit,
    onMode: (String) -> Unit,
    onFan: (String) -> Unit,
    onVerticalSwing: (Int) -> Unit,
    onHorizontalSwing: (Int) -> Unit,
    onThreeDAuto: (Boolean) -> Unit,
    onApplyProfile: (ClimateProfile, (Boolean, String?) -> Unit) -> Unit,
    airconConnected: Boolean,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("KLIMAPILOT", color = Blue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                Text(deviceManufacturer, color = TextMain, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(deviceModel, color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            Icon(
                Icons.Default.Cloud,
                contentDescription = if (airconConnected) "Verbunden" else "Offline",
                tint = if (airconConnected) Green else Muted,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            IconButton(onClick = onSettings, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Panel)) {
                Icon(Icons.Default.Settings, contentDescription = "Einstellungen", tint = TextMain)
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth()
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
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            SectionTitle("RAUM")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            rooms.forEach { room ->
                FilterChip(
                    selected = selectedRoom == room,
                    onClick = { onRoomSelect(room) },
                    label = { Text(room, maxLines = 1) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFE3EEF7),
                        selectedLabelColor = Color(0xFF123B60),
                        containerColor = Panel,
                        labelColor = TextMain
                    )
                )
            }
        }
        if (devicesInRoom.size > 1) {
            SectionTitle("KLIMAANLAGE")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                devicesInRoom.forEach { device ->
                    FilterChip(
                        selected = selectedDeviceId == device.id,
                        onClick = { onDeviceSelect(device.id) },
                        label = { Text(device.name, maxLines = 1, softWrap = false) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFE3EEF7),
                            selectedLabelColor = Color(0xFF123B60),
                            containerColor = Panel,
                            labelColor = TextMain
                        )
                    )
                }
            }
        }

        ClimateProfilesSection(
            deviceId = selectedDeviceId,
            syncDeviceKey = selectedDeviceSyncKey,
            devices = allDevices,
            currentState = airconState,
            onApply = onApplyProfile
        )

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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val state = airconState
                val ready = state != null && !controlBusy
                val modeLabel = when (state?.mode) {
                    Mode.AUTO -> "Auto"
                    Mode.COOL -> "Kühlen"
                    Mode.HEAT -> "Heizen"
                    Mode.DRY -> "Entfeuchten"
                    Mode.FAN -> "Lüfter"
                    null -> "Unbekannt"
                }
                val fanLabel = when (state?.airFlow) {
                    0 -> "Auto"; 1 -> "Leise"; 2 -> "Niedrig"; 3 -> "Mittel"; 4 -> "Hoch"; else -> "Unbekannt"
                }

                TemperatureCard(
                    deviceName = devicesInRoom.firstOrNull { it.id == deviceId }?.name ?: devicesInRoom.firstOrNull()?.name ?: selectedRoom,
                    state = state,
                    connected = airconConnected,
                    busy = controlBusy,
                    onPower = onPower,
                    onTargetTemp = onTargetTemp
                )

                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionTitle("BETRIEBSMODUS")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val modes = listOf(
                                Triple("Auto", "✦", "Auto"),
                                Triple("Kühlen", "❄", "Kühlen"),
                                Triple("Heizen", "☼", "Heizen"),
                                Triple("Lüfter", "≋", "Lüfter"),
                                Triple("Entfeuchten", "♢", "Trocknen")
                            )
                            modes.forEach { (value, symbol, label) ->
                                ModeTile(Modifier.weight(1f), label, symbol, modeLabel == value, ready) { onMode(value) }
                            }
                        }
                    }
                }

                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        SectionTitle("LÜFTER")
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("Auto", "Leise", "Niedrig", "Mittel", "Hoch").forEach { option ->
                                ChoicePill(option, fanLabel == option, ready) { onFan(option) }
                            }
                        }
                    }
                }

                val vertical = listOf("Auto", "Ganz oben", "Mitte oben", "Mitte unten", "Ganz unten")
                val horizontal = listOf("Auto", "Links außen", "Links", "Mitte links", "Mitte", "Mitte rechts", "Rechts", "Rechts außen")
                val supportsHorizontal = state != null && Codec.modelHasVaneLR(state.modelNrRaw) == true
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AirflowCard(
                        modifier = Modifier.weight(1f),
                        title = "VERTIKAL",
                        selected = state?.windUD?.let { vertical.getOrNull(it) } ?: "",
                        options = vertical,
                        enabled = ready,
                        onSelect = { onVerticalSwing(vertical.indexOf(it)) }
                    )
                    if (supportsHorizontal) {
                        AirflowCard(
                            modifier = Modifier.weight(1f),
                            title = "HORIZONTAL",
                            selected = horizontal.getOrNull(state!!.windLR) ?: "",
                            options = horizontal,
                            enabled = ready,
                            onSelect = { onHorizontalSwing(horizontal.indexOf(it)) }
                        )
                    }
                }

                if (supportsHorizontal && state != null) {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("3D-Auto-Luftstrom", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Verteilt die Luft im Raum", color = Muted, fontSize = 11.sp)
                            }
                            Switch(checked = state.entrust, onCheckedChange = onThreeDAuto, enabled = ready)
                        }
                    }
                }

                if (state != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactMetricCard(Modifier.weight(1f), "Kompressor", if (state.compressorRunning) "Aktiv" else "Inaktiv", state.errorCode?.let { "Statuscode: $it" } ?: "Kein Code gemeldet")
                        CompactMetricCard(Modifier.weight(1f), "Verbindung", if (airconConnected) "Lokal" else "Getrennt", if (controlBusy) "Befehl läuft …" else "WF-RAC")
                    }
                } else {
                    Text(
                        if (devicesInRoom.firstOrNull { it.id == deviceId }?.demoMode == true) "Demo-Gerät wird initialisiert …"
                        else "Kopple das WLAN-Modul in den Geräteeinstellungen oder aktiviere dort den Testmodus.",
                        color = Muted, fontSize = 12.sp
                    )
                }
                if (controlStatus.isNotBlank()) {
                    Text(controlStatus, color = if (controlStatus.contains("fehlgeschlagen", true) || controlStatus.contains("Sicherheitsprüfung", true)) MaterialTheme.colorScheme.error else Muted, fontSize = 11.sp)
                }
            }
        }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TemperatureCard(
    deviceName: String,
    state: AirconState?,
    connected: Boolean,
    busy: Boolean,
    onPower: (Boolean) -> Unit,
    onTargetTemp: (Float) -> Unit
) {
    var draftTemp by remember(state?.presetTemp) { mutableFloatStateOf((state?.presetTemp ?: 21.0).toFloat().coerceIn(16f, 30f)) }
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3EEF7))) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(deviceName, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(if (state == null) "Nicht verbunden" else if (state.power) "Eingeschaltet" else "Ausgeschaltet", color = if (connected && state?.power == true) Blue else Muted, fontSize = 11.sp)
                }
                Text(if (connected) "● Lokal verbunden" else "○ Offline", color = if (connected) Green else Muted, fontSize = 10.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("INNEN", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text(state?.indoorTemp?.let { String.format(Locale.GERMANY, "%.1f°", it) } ?: "—", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(enabled = state != null && !busy && draftTemp > 16f) {
                            draftTemp = (draftTemp - 0.5f).coerceAtLeast(16f); onTargetTemp(draftTemp)
                        }, color = Panel
                    ) { Box(contentAlignment = Alignment.Center) { Text("−", color = Blue, fontSize = 23.sp) } }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text(if (state == null) "—" else String.format(Locale.GERMANY, "%.1f", draftTemp), color = TextMain, fontSize = 38.sp, fontWeight = FontWeight.Light)
                        Text("SOLL °C", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Surface(
                        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(enabled = state != null && !busy && draftTemp < 30f) {
                            draftTemp = (draftTemp + 0.5f).coerceAtMost(30f); onTargetTemp(draftTemp)
                        }, color = Panel
                    ) { Box(contentAlignment = Alignment.Center) { Text("+", color = Blue, fontSize = 21.sp) } }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("AUSSEN", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text(state?.outdoorTemp?.let { String.format(Locale.GERMANY, "%.1f°", it) } ?: "—", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Slider(
                value = draftTemp,
                onValueChange = { value -> draftTemp = (value * 2f).roundToInt() / 2f },
                onValueChangeFinished = { onTargetTemp(draftTemp) },
                valueRange = 16f..30f,
                steps = 27,
                enabled = state != null && !busy,
                colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue, inactiveTrackColor = Color(0xFFC8D8E6)),
                modifier = Modifier.fillMaxWidth().height(28.dp)
            )
            Button(
                onClick = { onPower(!(state?.power ?: false)) },
                enabled = state != null && !busy,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state?.power == true) Color(0xFFD1E2F0) else Blue,
                    contentColor = if (state?.power == true) Color(0xFF123B60) else Color.White
                ),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state?.power == true) "Ausschalten" else "Einschalten", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ModeTile(modifier: Modifier, label: String, symbol: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier.height(88.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) Blue else Color(0xFFF2F5F8))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (label == "Kühlen") {
                Icon(Icons.Default.AcUnit, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (selected) Color.White else Muted)
            } else {
                Text(symbol, color = if (selected) Color.White else Muted, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
        Text(label, color = if (selected) Color.White else TextMain, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun ChoicePill(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick),
        color = if (selected) Color(0xFFE3EEF7) else Color(0xFFF3F6F9),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(label, Modifier.padding(horizontal = 11.dp, vertical = 9.dp), color = if (selected) Color(0xFF123B60) else TextMain, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun AirflowCard(modifier: Modifier, title: String, selected: String, options: List<String>, enabled: Boolean, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            Box(Modifier.fillMaxWidth()) {
                Surface(
                    onClick = { expanded = true },
                    enabled = enabled,
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF2F5F8),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selected.ifBlank { "—" }, color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Spacer(Modifier.weight(1f))
                        Text("▾", color = Muted, fontSize = 17.sp)
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                expanded = false
                                onSelect(option)
                            },
                            enabled = enabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMetricCard(modifier: Modifier, title: String, value: String, subtitle: String) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = Muted, fontSize = 10.sp)
            Text(value, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, color = Muted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
}

@Composable
internal fun WfRacConnectionCard(onConnectionChanged: (Boolean) -> Unit) {
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

    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PanelRaised)) {
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
private fun IoBrokerConnectionSettingsCard(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
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
    val url = (if (secure) "wss://" else "ws://") + address.trim() + ":" + (port.toIntOrNull() ?: 8084)
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PanelRaised)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth().clickable { onExpandedChange(!expanded) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ioBroker-Verbindung", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Für die Synchronisierung deiner Klimaprofile.", color = Muted, fontSize = 11.sp)
                }
                Text(if (expanded) "⌃" else "⌄", color = Muted, fontSize = 20.sp)
            }
            if (expanded) {
            OutlinedTextField(value = address, onValueChange = { address = it.trim() }, label = { Text("IP-Adresse oder Hostname") }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp), modifier = Modifier.fillMaxWidth().height(56.dp))
            OutlinedTextField(value = port, onValueChange = { port = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp), modifier = Modifier.fillMaxWidth().height(56.dp), isError = port.toIntOrNull()?.let { it !in 1..65535 } ?: true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                }, enabled = valid, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Text("Speichern", fontSize = 13.sp) }
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
                }, enabled = valid && !testing, modifier = Modifier.weight(1f).height(40.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) { Text(if (testing) "Prüfe …" else "Testen", fontSize = 13.sp) }
            }
            if (status.isNotBlank()) Text(status, color = if (status.contains("fehlgeschlagen") || status.contains("Bitte")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
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
    onManageProfiles: () -> Unit,
    fontScale: Float,
    onFontScale: (Float) -> Unit,
    onBack: () -> Unit
) {
    var accountExpanded by remember { mutableStateOf(false) }
    var fontSizeExpanded by remember { mutableStateOf(false) }
    var ioBrokerExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val peerDiscovery = remember(context) { ClimateProfilePeerDiscovery(context) }
    val lifecycleOwner = context as? LifecycleOwner
    var peerScanning by remember { mutableStateOf(false) }
    var peerScanMessage by remember { mutableStateOf("Noch keine lokale WLAN-Suche gestartet.") }
    val discoveredPeers = remember { mutableStateMapOf<String, String>() }
    DisposableEffect(peerDiscovery, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                peerDiscovery.stop()
                peerScanning = false
                discoveredPeers.clear()
                peerScanMessage = "WLAN-Suche wegen App-Hintergrund beendet."
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
            peerDiscovery.stop()
            peerScanning = false
            discoveredPeers.clear()
        }
    }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = PanelRaised)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth().clickable { accountExpanded = !accountExpanded }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Benutzerkonto", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${account.displayName} · ${if (isAdmin) "Administrator" else "Benutzer"}", color = Muted, fontSize = 11.sp)
                    }
                    Text(if (accountExpanded) "⌃" else "⌄", color = Muted, fontSize = 20.sp)
                }
                if (accountExpanded) {
                    OutlinedButton(onClick = onAccountSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Benutzerkonto und Benutzerwechsel", fontSize = 13.sp)
                    }
                }
            }
        }
        if (isAdmin) {
            Button(
                onClick = onManageRoomsDevices,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(9.dp))
                Text("Räume & Geräte verwalten", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (isAdmin) {
            Button(onClick = onManageProfiles, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                Icon(Icons.Default.Tune, contentDescription = null)
                Spacer(Modifier.width(9.dp))
                Text("Klimaprofile verwalten", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PanelRaised)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().clickable { fontSizeExpanded = !fontSizeExpanded }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Schriftgröße", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Aktuell: ${(fontScale * 100).toInt()}%", color = Muted, fontSize = 11.sp)
                    }
                    Text(if (fontSizeExpanded) "⌃" else "⌄", color = Muted, fontSize = 20.sp)
                }
                if (fontSizeExpanded) {
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
        }
        if (isAdmin) {
            IoBrokerConnectionSettingsCard(expanded = ioBrokerExpanded, onExpandedChange = { ioBrokerExpanded = it })
        } else {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = PanelRaised)) {
                Text(
                    "Verbindungs-, Raum- und Gerätekonfiguration kann nur ein Administrator ändern.",
                    modifier = Modifier.padding(14.dp), color = Muted, fontSize = 12.sp
                )
            }
        }
        if (BuildConfig.DEBUG) {
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PanelRaised)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Lokales WLAN", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Sucht nach anderen KlimaPilot-Geräten im selben Netzwerk. Gefundene Geräte sind zunächst nicht vertrauenswürdig.", color = Muted, fontSize = 11.sp)
                    Button(
                        onClick = {
                            if (peerScanning) {
                                peerDiscovery.stop()
                                peerScanning = false
                                discoveredPeers.clear()
                                peerScanMessage = "WLAN-Suche beendet."
                            } else {
                                discoveredPeers.clear()
                                peerScanMessage = "Suche im lokalen WLAN …"
                                peerScanning = true
                                val started = peerDiscovery.start(
                                    onPeerFound = { peer ->
                                        discoveredPeers[peer.instanceName] = "${peer.instanceName} · ${peer.hostAddress}:${peer.port}"
                                        peerScanMessage = "Unbestätigte Dienstankündigung gefunden."
                                    },
                                    onPeerLost = { instanceName ->
                                        discoveredPeers.remove(instanceName)
                                    },
                                    onError = { message ->
                                        peerScanning = false
                                        discoveredPeers.clear()
                                        peerScanMessage = message
                                    }
                                )
                                if (!started && peerScanning) {
                                    peerScanning = false
                                    peerScanMessage = "WLAN-Suche konnte nicht gestartet werden."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (peerScanning) "Suche beenden" else "KlimaPilot-Geräte suchen") }
                    Text(peerScanMessage, color = Muted, fontSize = 12.sp)
                    discoveredPeers.values.forEach { peer ->
                        Text(peer, color = TextMain, fontSize = 12.sp)
                        Text("Nicht gekoppelt · keine Datenübertragung", color = Muted, fontSize = 10.sp)
                    }
                }
            }
            ClimatePairingSettingsCard()
    
        }
        GithubUpdateCard(appVersion)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Zurück zur Klimaübersicht")
        }
        }
    }
}
