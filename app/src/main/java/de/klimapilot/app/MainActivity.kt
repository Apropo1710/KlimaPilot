package de.klimapilot.app

import android.os.Bundle
import android.content.Intent
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
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.Canvas
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import de.klimapilot.app.wfrac.AirconState
import de.klimapilot.app.wfrac.Mode
import de.klimapilot.app.wfrac.Codec
import de.klimapilot.app.sync.ClimateProfilePeerDiscovery
import de.klimapilot.app.sync.UnverifiedClimatePeer

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
        AppDiagnostics.init(this)
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
    var accountLoading by remember { mutableStateOf(true) }
    var accountStatus by remember { mutableStateOf("") }
    var accountUsername by remember { mutableStateOf("") }
    var accountPassword by remember { mutableStateOf("") }
    var accountPasswordConfirmation by remember { mutableStateOf("") }
    var accountDisplayName by remember { mutableStateOf("") }
    var accountIoBrokerAddress by remember {
        mutableStateOf(IoBrokerAddress.saved(prefs).orEmpty())
    }
    val rooms = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet("rooms", null)?.toList() ?: listOf("Wohnzimmer", "Schlafzimmer"))
            if (isEmpty()) addAll(listOf("Wohnzimmer", "Schlafzimmer"))
        }
    }
    fun readSavedDevices(): List<UserDevice> = mutableListOf<UserDevice>().apply {
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
    val devices = remember {
        mutableStateListOf<UserDevice>().apply { addAll(readSavedDevices()) }
    }
    fun saveData() {
        prefs.edit()
            .putLong(ClimatePeerDataSync.KEY_CONFIG_UPDATED_AT, System.currentTimeMillis())
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
    var showEnergy by remember { mutableStateOf(false) }
    var energyUnits by remember { mutableStateOf<List<EnergyUnitData>>(emptyList()) }
    var filterConfig by remember { mutableStateOf<FilterConfig?>(null) }
    // Set by the update popup: the settings open scrolled down to the update card.
    var settingsFocusUpdate by remember { mutableStateOf(false) }
    var energyPrice by remember { mutableStateOf(EnergyIoBroker.cachedPrice(context)) }
    val controller = remember(context) { ClimateDeviceController(context) }
    var currentAirconState by remember(selectedDevice?.id) { mutableStateOf<AirconState?>(null) }
    var airconConnected by remember(selectedDevice?.id) { mutableStateOf(false) }
    var controlBusy by remember(selectedDevice?.id) { mutableStateOf(false) }
    var controlStatus by remember(selectedDevice?.id) { mutableStateOf("") }
    // Bumped by every command: a poll that started before a command must not show older values.
    var commandSeq by remember(selectedDevice?.id) { mutableIntStateOf(0) }
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
            val seqAtStart = commandSeq
            runCatching { withContext(Dispatchers.IO) { controller.read(device) } }
                .onSuccess { state ->
                    if (seqAtStart == commandSeq && !controlBusy) { currentAirconState = state; controlStatus = "" }
                    airconConnected = true
                    AppDiagnostics.reach(device.name, true)
                }
                .onFailure { error ->
                    airconConnected = false
                    controlStatus = error.message ?: "Gerätestatus konnte nicht gelesen werden."
                    AppDiagnostics.reach(device.name, false, error.message)
                }
            delay(if (device.demoMode) 3_000L else 10_000L)
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
            commandSeq++
            controlStatus = "Befehl wird übertragen …"
            try {
                val updated = withContext(Dispatchers.IO) { controller.apply(device, change) }
                currentAirconState = updated
                airconConnected = true
                controlStatus = "Einstellung übernommen."
                onComplete?.invoke(true, controlStatus)
            } catch (error: Exception) {
                val message = error.message ?: "Steuerbefehl fehlgeschlagen."
                AppDiagnostics.log(device.name, "Befehl fehlgeschlagen", message)
                controlStatus = message
                onComplete?.invoke(false, message)
            } finally {
                commandSeq++
                controlBusy = false
            }
        }
    }

    /** Übernimmt die Kontenliste aus dem bereits verbundenen ioBroker. */
    fun importAccountsFromConnectedIoBroker(url: String) {
        IoBrokerClient.getJsonState(KlimaPilotAccountRepository.LEGACY_STATE_ID) { json, error ->
            IoBrokerClient.disconnect()
            val legacy = json?.let { KlimaPilotAccountRegistry.fromJson(it) }
            if (legacy != null) {
                if (accountRepository.saveInitial(legacy) || accountRepository.replaceEmptyWithImported(legacy)) {
                    accountRegistry = legacy
                    IoBrokerAddress.store(prefs, url)
                    accountIoBrokerAddress = url
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
                    accountStatus = "Die Konten wurden gelesen, konnten aber nicht lokal gespeichert werden. Vorhandene Daten bleiben erhalten."
                }
            } else if (error?.contains("existiert noch nicht", ignoreCase = true) == true) {
                IoBrokerAddress.store(prefs, url)
                accountIoBrokerAddress = url
                val empty = KlimaPilotAccountRegistry()
                if (accountRepository.saveInitial(empty)) {
                    accountRegistry = empty
                    accountStatus = ""
                } else {
                    accountStatus = "Lokale Konten konnten nicht initialisiert werden. Bitte erneut versuchen."
                }
            } else {
                accountStatus = "ioBroker wurde gefunden, aber die Kontenliste ist nicht lesbar: ${error ?: "ungültige Daten"}. Es wurde nichts verändert."
            }
            accountLoading = false
        }
    }

    /**
     * Automatische Einrichtung ohne Nutzereingabe: gespeicherte Adresse, sonst Suche im WLAN.
     * Erst wenn das scheitert, zeigt der Login die Adresseingabe und den QR-Fallback.
     */
    fun startAutomaticAccountSetup(manualUrl: String?) {
        val saved = IoBrokerAddress.saved(prefs)
        accountLoading = true
        accountStatus = ""
        val candidates = if (manualUrl != null) listOf(manualUrl)
            else listOfNotNull(saved, IoBrokerAddress.DEFAULT_URL).distinct()
        IoBrokerAutoSetup.connect(context, candidates, scanNetwork = manualUrl == null) { url, error ->
            if (url == null) {
                accountLoading = false
                accountIoBrokerAddress = manualUrl ?: saved ?: accountIoBrokerAddress.ifBlank { IoBrokerAddress.DEFAULT_URL }
                accountStatus = if (manualUrl != null) "Keine Verbindung zu $manualUrl: $error" else ""
                return@connect
            }
            importAccountsFromConnectedIoBroker(url)
        }
    }

    fun connectManualIoBrokerAddress() {
        val url = IoBrokerAddress.normalize(accountIoBrokerAddress)
        if (url == null) {
            accountStatus = "Bitte eine gültige Adresse eingeben, z. B. 192.168.178.54 oder 192.168.178.54:8084."
            return
        }
        startAutomaticAccountSetup(manualUrl = url)
    }

    fun scanSetupQrCode() {
        val activity = context as? android.app.Activity
        if (activity == null) {
            accountStatus = "Der QR-Scanner kann hier nicht geöffnet werden."
            return
        }
        val options = com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
            .enableAutoZoom().build()
        runCatching { com.google.mlkit.vision.codescanner.GmsBarcodeScanning.getClient(activity, options).startScan() }
            .onSuccess { task ->
                task.addOnSuccessListener { barcode ->
                    val payload = KlimaPilotSetupTransfer.parse(barcode.rawValue)
                    when {
                        payload == null ->
                            accountStatus = "Dieser QR-Code ist kein gültiger KlimaPilot-Einrichtungscode oder bereits abgelaufen. Bitte auf dem anderen Smartphone neu erzeugen."
                        !accountRepository.replaceEmptyWithImported(payload.registry) ->
                            accountStatus = "Auf diesem Smartphone sind bereits Konten vorhanden. Sie wurden nicht überschrieben."
                        else -> {
                            payload.ioBrokerUrl?.let { IoBrokerAddress.store(prefs, it); accountIoBrokerAddress = it }
                            accountRegistry = payload.registry
                            currentAccount = null
                            accountAuthenticated = false
                            accountUsername = ""
                            accountPassword = ""
                            accountStatus = ""
                        }
                    }
                }.addOnCanceledListener {
                    accountStatus = ""
                }.addOnFailureListener { error ->
                    accountStatus = "QR-Scanner konnte nicht gestartet werden: ${error.message ?: "unbekannter Fehler"}"
                }
            }
            .onFailure { error -> accountStatus = "QR-Scanner konnte nicht gestartet werden: ${error.message ?: "unbekannter Fehler"}" }
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
        startAutomaticAccountSetup(manualUrl = null)
    }

    fun saveRegistry(expectedRevision: Long, next: KlimaPilotAccountRegistry, onSaved: (Boolean, String?) -> Unit) {
        accountRepository.save(expectedRevision, next) { saved, error ->
            if (saved) accountRegistry = next
            onSaved(saved, error)
        }
    }

    fun loadExistingAccountsFromIoBroker() {
        val address = IoBrokerAddress.normalize(accountIoBrokerAddress)
        if (address == null) {
            accountStatus = "Bitte eine gültige Adresse eingeben, z. B. 192.168.178.54 oder 192.168.178.54:8084."
            return
        }
        accountLoading = true
        accountStatus = "Verbindung zu ioBroker wird hergestellt …"
        IoBrokerClient.connect(address, subscribeAll = false) { connected, message ->
            if (!connected) {
                IoBrokerClient.disconnect()
                accountLoading = false
                accountStatus = "Verbindung zu ioBroker fehlgeschlagen: $message"
                return@connect
            }
            accountStatus = "Vorhandene Konten werden geladen …"
            IoBrokerClient.getJsonState(KlimaPilotAccountRepository.LEGACY_STATE_ID) { json, error ->
                IoBrokerClient.disconnect()
                val existing = json?.let { KlimaPilotAccountRegistry.fromJson(it) }
                when {
                    existing == null -> {
                        accountLoading = false
                        accountStatus = "Die vorhandene Kontenliste konnte nicht aus ioBroker gelesen werden: ${error ?: "Die Daten sind ungültig."} Die lokale Einrichtung wurde nicht verändert."
                    }
                    existing.accounts.isEmpty() -> {
                        accountLoading = false
                        accountStatus = "Im ioBroker wurde eine gültige, aber leere Kontenliste gefunden. Es sind dort keine vorhandenen Benutzerkonten vorhanden."
                    }
                    !accountRepository.replaceEmptyWithImported(existing) -> {
                        accountLoading = false
                        accountStatus = "Die lokale Kontenliste ist nicht mehr leer oder nicht lesbar. Sie wurde vorsichtshalber nicht überschrieben."
                    }
                    else -> {
                        accountRegistry = existing
                        IoBrokerAddress.store(prefs, address)
                        accountUsername = ""
                        accountPassword = ""
                        accountPasswordConfirmation = ""
                        accountLoading = false
                        accountStatus = "Vorhandene Konten geladen. Du kannst dich jetzt mit deinem bisherigen Benutzerkonto anmelden."
                    }
                }
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

    LaunchedEffect(Unit) { loadAccounts() }
    ClimatePeerAutoSync(enabled = accountAuthenticated && currentAccount != null)
    LaunchedEffect(PeerSyncSignals.dataRevision) {
        if (PeerSyncSignals.dataRevision == 0) return@LaunchedEffect
        val savedRooms = prefs.getStringSet("rooms", null)?.toList().orEmpty()
        if (savedRooms.isNotEmpty() && savedRooms.toSet() != rooms.toSet()) { rooms.clear(); rooms.addAll(savedRooms) }
        val savedDevices = readSavedDevices()
        if (savedDevices != devices.toList()) { devices.clear(); devices.addAll(savedDevices) }
        IoBrokerAddress.saved(prefs)?.let { if (accountIoBrokerAddress.isBlank()) accountIoBrokerAddress = it }
        val loaded = accountRepository.load() as? KlimaPilotAccountLoadResult.Loaded ?: return@LaunchedEffect
        accountRegistry = loaded.registry
        currentAccount?.let { account ->
            val updated = loaded.registry.find(account.username)
            if (updated != null) currentAccount = updated
            else { currentAccount = null; accountAuthenticated = false }
        }
    }

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
            ioBrokerAddress = accountIoBrokerAddress,
            onIoBrokerAddress = { accountIoBrokerAddress = it },
            onLoadExistingAccounts = { loadExistingAccountsFromIoBroker() },
            onConnectManualAddress = { connectManualIoBrokerAddress() },
            onScanSetupQr = { scanSetupQrCode() },
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
            onDeleteAccount = { username -> deleteAccount(username) },
            setupQrPayload = {
                accountRegistry?.let { KlimaPilotSetupTransfer.create(it, IoBrokerAddress.saved(prefs)) }
            },
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
            energyUnits = energyUnits,
            onFilterChanged = { filterConfig = it },
            focusUpdate = settingsFocusUpdate,
            onFocusUpdateDone = { settingsFocusUpdate = false },
            onBack = { showSettings = false; settingsFocusUpdate = false }
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
            onSettings = { showSettings = true },
            onShowEnergy = { showEnergy = true },
            todayEnergyKwh = energyUnits.firstOrNull { it.airconId == selectedDevice?.wfRacAirconId }
                ?.let { EnergyMath.todayKwh(it, currentAirconState?.runEnergyKwh, java.time.LocalDateTime.now()) },
            energyPrice = energyPrice,
            filtersDue = energyUnits.firstOrNull { it.airconId == selectedDevice?.wfRacAirconId }
                ?.let { unit -> filterConfig?.dueFilters(unit) }.orEmpty(),
            onFilterDone = { kind ->
                energyUnits.firstOrNull { it.airconId == selectedDevice?.wfRacAirconId }?.let { unit ->
                    FilterIoBroker().update(prefs.getString("iobroker_server", null).orEmpty(), { it.cleaned(kind, unit) }) { result, _ ->
                        result?.let { filterConfig = it }
                    }
                }
            }
        )
        if (showEnergy) EnergyDialog(devices.toList(), selectedDevice) { showEnergy = false }
    }

    // Keeps the unit list of the ioBroker energy script current (writes only on changes) and
    // fetches the recorded values for today's consumption on the main page every 5 minutes.
    val energyConfigSignature = devices.joinToString("|") { "${it.wfRacAirconId}@${it.wfRacHost}/${it.name}/${it.room}/${it.demoMode}" }
    LaunchedEffect(accountAuthenticated, energyConfigSignature, showEnergy) {
        if (!accountAuthenticated || showEnergy) return@LaunchedEffect
        val address = prefs.getString("iobroker_server", null).orEmpty()
        if (address.isBlank() || devices.none { it.wfRacAirconId.isNotBlank() }) return@LaunchedEffect
        var first = true
        while (true) {
            EnergyIoBroker().exchange(address, devices.toList(), null) { result, _ ->
                result?.let {
                    EnergyIoBroker.cachePrice(context, it.priceEurPerKwh)
                    energyPrice = it.priceEurPerKwh
                    energyUnits = it.units
                }
            }
            FilterIoBroker().load(address) { result, _ -> result?.let { filterConfig = it } }
            // Brings an energy script installed by KlimaPilot up to the version shipped with the app.
            if (first) {
                EnergyScriptInstaller(context).install(address, updateOnly = true) { _, _ -> }
                // Takes over the diagnostics switch set on the other phone.
                DiagnosticsIoBroker().load(address) { result, _ -> result?.let { AppDiagnostics.setEnabledLocal(it.enabled) } }
            }
            first = false
            delay(300_000L)
        }
    }

    if (accountAuthenticated) {
        StartupUpdateCheck(
            appVersion = appVersion,
            onOpenSettings = {
                showManager = false
                showAccountSettings = false
                settingsFocusUpdate = true
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
    onSettings: () -> Unit,
    onShowEnergy: () -> Unit,
    todayEnergyKwh: Double?,
    energyPrice: Double,
    filtersDue: List<Pair<FilterKind, Double>> = emptyList(),
    onFilterDone: (FilterKind) -> Unit = {}
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

        // Rooms (and units, if a room has several) stay fixed under the header.
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
        }

        // Switching room or unit always starts at the top of the page.
        val pageScroll = rememberScrollState()
        LaunchedEffect(selectedRoom, selectedDeviceId) { pageScroll.scrollTo(0) }
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
                .verticalScroll(pageScroll).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))

        filtersDue.forEach { (kind, hours) -> FilterReminder(kind, hours) { onFilterDone(kind) } }

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
                            Switch(checked = state.entrust, onCheckedChange = onThreeDAuto, enabled = ready, colors = blueSwitchColors())
                        }
                    }
                }

                ScheduleSection(allDevices.firstOrNull { it.id == deviceId }, airconState)

                // The recorded consumption comes from ioBroker, so it stays visible while the unit is unreachable.
                if (state != null || todayEnergyKwh != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (todayEnergyKwh != null) {
                            CompactMetricCard(
                                Modifier.weight(1f).clickable(onClick = onShowEnergy), "Verbrauch heute",
                                "%.2f kWh".format(java.util.Locale.GERMANY, todayEnergyKwh),
                                "≈ %.2f € · antippen für Verlauf".format(java.util.Locale.GERMANY, todayEnergyKwh * energyPrice)
                            )
                        } else if (state != null) {
                            // No recording in ioBroker yet: the unit's own counter is all there is.
                            CompactMetricCard(
                                Modifier.weight(1f).clickable(onClick = onShowEnergy), "Verbrauch",
                                state.runEnergyKwh?.let { "%.2f kWh".format(java.util.Locale.GERMANY, it) } ?: "Nicht gemeldet",
                                "Seit dem Einschalten · antippen für Verlauf"
                            )
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
                        devicesInRoom.firstOrNull { it.id == deviceId }.let { device ->
                            when {
                                device?.demoMode == true -> "Demo-Gerät wird initialisiert …"
                                device?.wfRacAirconId.isNullOrBlank() -> "Kopple das WLAN-Modul in den Geräteeinstellungen oder aktiviere dort den Testmodus."
                                controlStatus.isBlank() -> "Verbindung zur Klimaanlage wird aufgebaut …"
                                else -> "Klimaanlage im Moment nicht erreichbar. Die App versucht es alle 10 Sekunden erneut."
                            }
                        },
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
    // Clicks only change the shown value; it is sent once the user pauses, so several
    // 0.5° steps become one command and the buttons never lock in between.
    var draftTemp by remember { mutableFloatStateOf((state?.presetTemp ?: 21.0).toFloat().coerceIn(16f, 30f)) }
    var editSeq by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }
    val currentBusy by rememberUpdatedState(busy)
    val currentState by rememberUpdatedState(state)
    val currentOnTargetTemp by rememberUpdatedState(onTargetTemp)
    LaunchedEffect(state?.presetTemp) {
        if (!editing) draftTemp = (state?.presetTemp ?: 21.0).toFloat().coerceIn(16f, 30f)
    }
    LaunchedEffect(editSeq) {
        if (editSeq == 0) return@LaunchedEffect
        delay(1_200)
        snapshotFlow { currentBusy }.first { !it }
        val sent = draftTemp
        currentOnTargetTemp(sent)
        // Keep the shown value until the command is through; then follow the unit again.
        if (withTimeoutOrNull(3_000) { snapshotFlow { currentBusy }.first { it } } != null) {
            snapshotFlow { currentBusy }.first { !it }
        }
        editing = false
        currentState?.presetTemp?.let { draftTemp = it.toFloat().coerceIn(16f, 30f) }
    }
    fun changeTemp(value: Float) {
        draftTemp = value.coerceIn(16f, 30f)
        editing = true
        editSeq++
    }
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
                        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(enabled = state != null && draftTemp > 16f) {
                            changeTemp(draftTemp - 0.5f)
                        }, color = Panel
                    ) { Box(contentAlignment = Alignment.Center) { Text("−", color = Blue, fontSize = 23.sp) } }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text(if (state == null) "—" else String.format(Locale.GERMANY, "%.1f", draftTemp), color = TextMain, fontSize = 38.sp, fontWeight = FontWeight.Light)
                        Text("SOLL °C", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Surface(
                        modifier = Modifier.size(36.dp).clip(CircleShape).clickable(enabled = state != null && draftTemp < 30f) {
                            changeTemp(draftTemp + 0.5f)
                        }, color = Panel
                    ) { Box(contentAlignment = Alignment.Center) { Text("+", color = Blue, fontSize = 21.sp) } }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("AUSSEN", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                    Text(state?.outdoorTemp?.let { String.format(Locale.GERMANY, "%.1f°", it) } ?: "—", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            TemperatureSlider(
                value = draftTemp,
                enabled = state != null,
                onValueChange = { value -> draftTemp = value; editing = true },
                onValueChangeFinished = { changeTemp(draftTemp) }
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
private fun EnergyScriptSettingsCard(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("klimapilot_settings", android.content.Context.MODE_PRIVATE) }
    val installer = remember { EnergyScriptInstaller(context) }
    var installing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PanelRaised)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable { onExpandedChange(!expanded) }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("ioBroker-Skript", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Für Energieverlauf und Zeitpläne.", color = Muted, fontSize = 11.sp)
                }
                Text(if (expanded) "⌃" else "⌄", color = Muted, fontSize = 20.sp)
            }
            if (expanded) {
                Text(
                    "KlimaPilot legt im ioBroker-JavaScript-Adapter das Skript „KlimaPilot“ an und startet es. Es zeichnet alle 5 Minuten den Verbrauch auf und führt die Zeitpläne aus. Ältere Kopien werden angehalten bzw. ersetzt. Beim App-Start wird das Skript bei Bedarf automatisch aktualisiert.",
                    color = Muted, fontSize = 12.sp
                )
                OutlinedButton(
                    onClick = {
                        installing = true
                        status = "Wird eingerichtet …"
                        installer.install(prefs.getString("iobroker_server", null).orEmpty(), updateOnly = false) { _, message ->
                            installing = false
                            status = message
                        }
                    },
                    enabled = !installing,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) { Text("Skript in ioBroker einrichten / prüfen", fontSize = 13.sp) }
                if (status.isNotBlank()) Text(status, color = TextMain, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun IoBrokerConnectionSettingsCard(expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("klimapilot_settings", android.content.Context.MODE_PRIVATE) }
    fun initialAddress(): String {
        val configured = prefs.getString("klimapilot_iobroker_address", null)
        if (!configured.isNullOrBlank() && configured.contains("://")) {
            return configured.substringAfter("://").substringBefore("/").substringBefore("?").substringBeforeLast(":")
        }
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
    energyUnits: List<EnergyUnitData> = emptyList(),
    onFilterChanged: (FilterConfig) -> Unit = {},
    focusUpdate: Boolean = false,
    onFocusUpdateDone: () -> Unit = {},
    onBack: () -> Unit
) {
    val settingsScroll = rememberScrollState()
    LaunchedEffect(focusUpdate) {
        if (!focusUpdate) return@LaunchedEffect
        // The update card sits at the end of the settings; wait for the layout, then scroll down.
        delay(250)
        settingsScroll.animateScrollTo(settingsScroll.maxValue)
        onFocusUpdateDone()
    }
    var accountExpanded by remember { mutableStateOf(false) }
    var fontSizeExpanded by remember { mutableStateOf(false) }
    var ioBrokerExpanded by remember { mutableStateOf(false) }
    var energyExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
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
                .verticalScroll(settingsScroll)
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
            EnergyScriptSettingsCard(expanded = energyExpanded, onExpandedChange = { energyExpanded = it })
            var diagnosticsExpanded by remember { mutableStateOf(false) }
            DiagnosticsSettingsCard(expanded = diagnosticsExpanded, onExpandedChange = { diagnosticsExpanded = it })
            var filterExpanded by remember { mutableStateOf(false) }
            FilterSettingsCard(energyUnits, expanded = filterExpanded, onExpandedChange = { filterExpanded = it }, onChanged = onFilterChanged)
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = PanelRaised)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScheduleSettingsContent()
                }
            }
        } else {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = PanelRaised)) {
                Text(
                    "Verbindungs-, Raum- und Gerätekonfiguration kann nur ein Administrator ändern.",
                    modifier = Modifier.padding(14.dp), color = Muted, fontSize = 12.sp
                )
            }
        }
        ClimatePairingSettingsCard()
        GithubUpdateCard(appVersion)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Zurück zur Klimaübersicht")
        }
        }
    }
}


/**
 * Temperature slider that only moves when the round knob itself is grabbed. A swipe that starts
 * elsewhere on the bar is left to the room swipe, and a tap on the bar changes nothing.
 */
@Composable
private fun TemperatureSlider(value: Float, enabled: Boolean, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit) {
    val min = 16f
    val max = 30f
    val currentValue by rememberUpdatedState(value)
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    var dragging by remember { mutableStateOf(false) }
    val knobRadius = if (dragging) 13.dp else 11.dp
    Canvas(
        Modifier.fillMaxWidth().height(32.dp).pointerInput(Unit) {
            val pad = 14.dp.toPx()
            val grab = 26.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (!currentEnabled) return@awaitEachGesture
                val width = size.width - 2 * pad
                val knobX = pad + (currentValue - min) / (max - min) * width
                if (kotlin.math.abs(down.position.x - knobX) > grab) return@awaitEachGesture
                // Grabbed the knob: from now on this gesture belongs to the slider.
                down.consume()
                dragging = true
                var last = currentValue
                drag(down.id) { change ->
                    change.consume()
                    val raw = min + ((change.position.x - pad) / width).coerceIn(0f, 1f) * (max - min)
                    val stepped = (raw * 2f).roundToInt() / 2f
                    if (stepped != last) { last = stepped; currentOnChange(stepped) }
                }
                dragging = false
                currentOnFinished()
            }
        }
    ) {
        val pad = 14.dp.toPx()
        val width = size.width - 2 * pad
        val y = size.height / 2
        val fraction = ((value - min) / (max - min)).coerceIn(0f, 1f)
        val knobX = pad + fraction * width
        val track = 6.dp.toPx()
        val active = if (enabled) Blue else Muted
        drawLine(Color(0xFFC8D8E6), androidx.compose.ui.geometry.Offset(pad, y), androidx.compose.ui.geometry.Offset(pad + width, y), strokeWidth = track, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(active, androidx.compose.ui.geometry.Offset(pad, y), androidx.compose.ui.geometry.Offset(knobX, y), strokeWidth = track, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // One dot per whole degree.
        for (degree in 17 until 30) {
            val x = pad + (degree - min) / (max - min) * width
            drawCircle(if (x <= knobX) Color.White.copy(alpha = 0.7f) else active.copy(alpha = 0.45f), radius = 1.5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
        }
        drawCircle(Color.White, radius = knobRadius.toPx() + 2.dp.toPx(), center = androidx.compose.ui.geometry.Offset(knobX, y))
        drawCircle(active, radius = knobRadius.toPx(), center = androidx.compose.ui.geometry.Offset(knobX, y))
    }
}
