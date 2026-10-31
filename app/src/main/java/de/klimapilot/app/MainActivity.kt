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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
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
    var showSettings by remember { mutableStateOf(false) }
    BackHandler(enabled = showSettings) { showSettings = false }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("klimapilot_settings", android.content.Context.MODE_PRIVATE) }
    val rooms = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("rooms", null)?.toList() ?: listOf("Wohnzimmer", "Schlafzimmer")) } }
    val devices = remember { mutableStateListOf<UserDevice>().apply {
        val saved = prefs.getString("devices", null)
        if (saved.isNullOrBlank()) { add(UserDevice("1","Klimaanlage","Wohnzimmer")); add(UserDevice("2","Klimaanlage","Schlafzimmer")) }
        else runCatching {
            val a=org.json.JSONArray(saved)
            for(i in 0 until a.length()) {
                val o=a.getJSONObject(i)
                val mappingJson=o.optJSONObject("mappings") ?: org.json.JSONObject()
                val mappings=mutableMapOf<String,String>()
                mappingJson.keys().forEach { key -> mappings[key]=mappingJson.optString(key) }
                add(UserDevice(o.getString("id"),o.getString("name"),o.getString("room"),o.optString("adapterKey","generic"),o.optString("adapterInstance",""),o.optString("rootObjectId",""),mappings,o.optString("model","")))
            }
        }
    } }
    fun saveData() { prefs.edit().putStringSet("rooms",rooms.toSet()).putString("devices",org.json.JSONArray().apply { devices.forEach { put(org.json.JSONObject().put("id",it.id).put("name",it.name).put("room",it.room).put("adapterKey",it.adapterKey).put("adapterInstance",it.adapterInstance).put("rootObjectId",it.rootObjectId).put("mappings",org.json.JSONObject(it.mappings)).put("model",it.model)) } }.toString()).apply() }
    var selectedRoom by remember { mutableStateOf(rooms.firstOrNull() ?: "Wohnzimmer") }
    var selectedDeviceId by remember { mutableStateOf(devices.firstOrNull { it.room == selectedRoom }?.id ?: devices.firstOrNull()?.id ?: "") }
    val devicesInSelectedRoom = devices.filter { it.room == selectedRoom }
    val selectedDevice = devicesInSelectedRoom.firstOrNull { it.id == selectedDeviceId }
        ?: devicesInSelectedRoom.firstOrNull()
    val allDevices = devicesInSelectedRoom
    val roomStates = remember { mutableStateMapOf("Wohnzimmer" to DemoRoomState("25,4 °C",true,23f,"Kühlen","Auto"),"Schlafzimmer" to DemoRoomState("22,1 °C",false,21f,"Aus","Leise")) }
    var showManager by remember { mutableStateOf(false) }
    BackHandler(enabled = showSettings || showManager) { if(showManager) showManager=false else showSettings=false }
    val room = roomStates.getOrPut(selectedRoom) { DemoRoomState("—",false,21f,"Aus","Auto") }
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

    fun connectToIoBroker() {
        val target = serverUrl.trim().trimEnd('/')
        if (!(target.startsWith("ws://") || target.startsWith("wss://")) || target.contains("xxx")) {
            ioBrokerConnected = false
            ioBrokerStatus = "Bitte eine gültige Serveradresse eingeben."
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
        }
    }

    LaunchedEffect(Unit) {
        if (serverUrl.isNotBlank() && !serverUrl.contains("xxx")) connectToIoBroker()
    }

    if (showManager) {
        RoomDeviceManagerScreen(rooms,devices,::saveData,{old,new -> if(selectedRoom==old)selectedRoom=new;roomStates.remove(old)?.let{roomStates[new]=it}},{ removed -> if(selectedRoom==removed)selectedRoom=rooms.first(); roomStates.remove(removed) }){showManager=false}
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
            onManageRoomsDevices = { showManager = true; showSettings = false },
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
            devicesInRoom = allDevices,
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
            deviceManufacturer = selectedDevice?.let { device -> AdapterProfiles.all.firstOrNull { it.key == device.adapterKey }?.title?.takeUnless { it.startsWith("Universell") } ?: "Klimaanlage" } ?: "Kein Gerät zugeordnet",
            deviceModel = selectedDevice?.model?.takeIf { it.isNotBlank() } ?: "Modell nicht hinterlegt",
            isOn = room.isOn,
            targetTemp = room.targetTemp,
            onTargetTemp = { room.targetTemp = it },
            mode = room.mode,
            onMode = {
                room.mode = it
                room.isOn = it != "Aus"
            },
            fan = room.fan,
            onFan = { room.fan = it },
            ioBrokerConnected = ioBrokerConnected,
            onSettings = { showSettings = true }
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
private fun SettingsScreen(serverUrl: String, onServerUrlChange: (String) -> Unit, onServerUrlConfirm: () -> Unit, connected: Boolean, checking: Boolean, status: String, onConnect: () -> Unit, appVersion: String, onManageRoomsDevices: () -> Unit, onBack: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = TextMain) }
            Column {
                Text("Einstellungen", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Verbindung und Geräte", color = Muted, fontSize = 12.sp)
            }
        }
        OutlinedButton(onClick = onManageRoomsDevices, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Tune, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Räume & Geräte verwalten")
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFDCEAF2)),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF398FBA)), contentAlignment = Alignment.Center) {
                        Icon(if (connected) Icons.Default.Cloud else Icons.Default.CloudOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("ioBroker-Verbindung", color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(if (connected) "Verbunden" else if (checking) "Verbindung wird aufgebaut …" else "Nicht verbunden", color = if (connected) Green else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                        }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Eingabe bestätigen", tint = Green)
                        }
                    }
                )
                val statusHasError = listOf("fehlgeschlagen", "timeout", "nicht geantwortet", "send failed", "Fehler", "disconnected", "konnte nicht gelesen werden").any { status.contains(it, ignoreCase = true) }
                val statusColor = when {
                    statusHasError -> Color(0xFFD32F2F)
                    connected && !status.contains("prüfe", ignoreCase = true) -> Green
                    else -> Muted
                }
                Text(status, color = statusColor, fontSize = 12.sp, fontWeight = if (connected && !statusHasError) FontWeight.Medium else FontWeight.Normal)
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
                    Text("Live-Daten und Steuerung laufen über diese Verbindung.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
        GithubUpdateCard(appVersion)
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Zurück zur Klimaübersicht") }
    }
}
