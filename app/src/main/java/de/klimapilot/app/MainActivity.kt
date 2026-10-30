package de.klimapilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
        else runCatching { val a=org.json.JSONArray(saved); for(i in 0 until a.length()){ val o=a.getJSONObject(i); add(UserDevice(o.getString("id"),o.getString("name"),o.getString("room"))) } }
    } }
    fun saveData() { prefs.edit().putStringSet("rooms",rooms.toSet()).putString("devices",org.json.JSONArray().apply { devices.forEach { put(org.json.JSONObject().put("id",it.id).put("name",it.name).put("room",it.room)) } }.toString()).apply() }
    var selectedRoom by remember { mutableStateOf(rooms.firstOrNull() ?: "Wohnzimmer") }
    val roomStates = remember { mutableStateMapOf("Wohnzimmer" to DemoRoomState("25,4 °C",true,23f,"Kühlen","Auto"),"Schlafzimmer" to DemoRoomState("22,1 °C",false,21f,"Aus","Leise")) }
    var showManager by remember { mutableStateOf(false) }
    BackHandler(enabled = showSettings || showManager) { if(showManager) showManager=false else showSettings=false }
    val room = roomStates.getOrPut(selectedRoom) { DemoRoomState("—",false,21f,"Aus","Auto") }
    var vpnDemoConnected by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf("192.168.178.xxx") }
    var port by remember { mutableStateOf("8087") }

    if (showManager) {
        RoomDeviceManagerScreen(rooms,devices,::saveData,{old,new -> if(selectedRoom==old)selectedRoom=new;roomStates.remove(old)?.let{roomStates[new]=it}},{ removed -> if(selectedRoom==removed)selectedRoom=rooms.first(); roomStates.remove(removed) }){showManager=false}
    } else if (showSettings) {
        SettingsScreen(
            host = host,
            onHostChange = { host = it },
            port = port,
            onPortChange = { port = it },
            appVersion = appVersion,
            onBack = { showSettings = false }
        )
    } else {
        MainScreen(
            selectedRoom = selectedRoom,
            rooms = rooms,
            onRoomSelect = { selectedRoom = it },
            onManageRoomsDevices = { showManager = true },
            roomTemperature = room.roomTemperature,
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
            vpnDemoConnected = vpnDemoConnected,
            onVpnDemo = { vpnDemoConnected = !vpnDemoConnected },
            onSettings = { showSettings = true }
        )
    }
}

@Composable
private fun MainScreen(
    selectedRoom: String,
    rooms: List<String>,
    onRoomSelect: (String) -> Unit,
    onManageRoomsDevices: () -> Unit,
    roomTemperature: String,
    isOn: Boolean,
    targetTemp: Float,
    onTargetTemp: (Float) -> Unit,
    mode: String,
    onMode: (String) -> Unit,
    fan: String,
    onFan: (String) -> Unit,
    vpnDemoConnected: Boolean,
    onVpnDemo: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("KLIMAPILOT", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Mitsubishi Heavy", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("SRK15ZS-WF · App-Prototyp", color = Muted, fontSize = 13.sp)
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Panel)) {
                Icon(Icons.Default.Settings, contentDescription = "Einstellungen", tint = TextMain)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF3D6)).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF9A6700), modifier = Modifier.size(18.dp))
            Text("DEMO-MODUS · Keine echte Verbindung", color = Color(0xFF7A4B00), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }

        SectionTitle("RAUM AUSWÄHLEN")
        FlowModeSelector(selected = selectedRoom, options = rooms, onSelect = onRoomSelect)
        OutlinedButton(onClick=onManageRoomsDevices,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Tune,contentDescription=null);Spacer(Modifier.width(6.dp));Text("Räume & Geräte verwalten")}

        TemperatureCard(
            roomName = selectedRoom,
            roomTemperature = roomTemperature,
            isOn = isOn,
            targetTemp = targetTemp,
            onTargetTemp = onTargetTemp
        )

        SectionTitle("BETRIEBSMODUS")
        FlowModeSelector(selected = mode, options = listOf("Aus", "Auto", "Kühlen", "Heizen", "Entfeuchten", "Lüfter"), onSelect = onMode)

        SectionTitle("LÜFTERSTUFE")
        FlowModeSelector(selected = fan, options = listOf("Auto", "Leise", "1", "2", "3", "4"), onSelect = onFan)

        SectionTitle("FERNZUGRIFF")
        VpnDemoCard(connected = vpnDemoConnected, onToggle = onVpnDemo)

        Text(
            "Alle Werte und Schalter sind lokal simuliert. Die App sendet keine Befehle an Geräte und steuert WireGuard noch nicht.",
            color = Muted, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TemperatureCard(
    roomName: String,
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
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (isOn) Green else Color(0xFFD93025)))
            Text(
                if (isOn) "${roomName.uppercase()} · KLIMA LÄUFT (DEMO)" else "${roomName.uppercase()} · KLIMA AUS (DEMO)",
                color = if (isOn) Green else Color(0xFFD93025),
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
                    Text("${String.format(Locale.GERMANY, "%.1f", targetTemp)}°", color = TextMain, fontSize = 42.sp, fontWeight = FontWeight.Light)
                }
            }
        }
        Text("Solltemperatur", color = Muted, fontSize = 13.sp)
        Slider(
            value = targetTemp,
            onValueChange = onTargetTemp,
            valueRange = 16f..30f,
            steps = 139,
            enabled = isOn,
            colors = SliderDefaults.colors(thumbColor = Blue, activeTrackColor = Blue, inactiveTrackColor = PanelRaised),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("16 °C", color = Muted, fontSize = 12.sp)
            Text("Tippen oder schieben zum Testen", color = Muted, fontSize = 11.sp)
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
private fun FlowModeSelector(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(option) },
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
private fun VpnDemoCard(connected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(PanelRaised), contentAlignment = Alignment.Center) {
            Icon(if (connected) Icons.Default.Shield else Icons.Default.CloudOff, contentDescription = null, tint = if (connected) Green else Muted, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("WireGuard", color = TextMain, fontWeight = FontWeight.SemiBold)
            Text(if (connected) "Demo: als verbunden markiert" else "Demo: nicht verbunden", color = if (connected) Green else Muted, fontSize = 12.sp)
        }
        OutlinedButton(onClick = onToggle, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Text(if (connected) "Trennen" else "Verbinden", fontSize = 12.sp)
        }
    }
    Text("Der Button simuliert nur den Status. Er schaltet den echten VPN-Tunnel nicht.", color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(host: String, onHostChange: (String) -> Unit, port: String, onPortChange: (String) -> Unit, appVersion: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Zurück", tint = TextMain) }
            Column {
                Text("Einstellungen", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Vorbereitung für die spätere Verbindung", color = Muted, fontSize = 12.sp)
            }
        }
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF3D6)).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF9A6700))
            Text("Noch ohne Netzwerkfunktion. Einstellungen werden nicht übertragen.", color = Color(0xFF7A4B00), fontSize = 12.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(26.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text("ioBroker-Verbindung", color = TextMain, fontWeight = FontWeight.SemiBold)
                    Text("Noch nicht verbunden · Demo", color = Color(0xFF9A6700), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Die echte Verbindungsprüfung wird eingerichtet, sobald die Schnittstelle feststeht.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
        Text("ioBroker-Adresse", color = TextMain, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(value = host, onValueChange = onHostChange, modifier = Modifier.fillMaxWidth(), label = { Text("IP-Adresse oder Hostname") }, singleLine = true)
        OutlinedTextField(value = port, onValueChange = onPortChange, modifier = Modifier.fillMaxWidth(), label = { Text("Port (später festlegen)") }, singleLine = true)
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = Blue)
                Text("Später einzurichten", color = TextMain, fontWeight = FontWeight.SemiBold)
                Text("• Sichere ioBroker-Schnittstelle\n• Zugriff über dein WireGuard-VPN\n• Freigegebene Datenpunkte der Klimaanlage\n• Verbindungsprüfung und Fehlermeldungen", color = Muted, lineHeight = 22.sp, fontSize = 13.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("App-Version", color = TextMain, fontWeight = FontWeight.SemiBold)
                    Text("Installierte Version", color = Muted, fontSize = 12.sp)
                }
                Text(appVersion, color = Blue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Zurück zur Klimaübersicht") }
    }
}
