package de.klimapilot.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.klimapilot.app.wfrac.AirconState
import java.util.Locale
import java.util.UUID

/**
 * Full-screen profile editor in the style of the schedule editor. Every setting has a switch:
 * on means the profile sets this value, off means the value stays as it is on the unit.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ClimateProfileEditorDialog(
    deviceId: String,
    profile: ClimateProfile?,
    initialState: AirconState? = null,
    currentSeason: String = ProfileSeason.WINTER,
    modelSupportsVaneLR: Boolean?,
    onDismiss: () -> Unit,
    onSave: (ClimateProfile) -> Unit
) {
    val key = profile?.id
    val fromState = initialState != null
    var name by remember(key) { mutableStateOf(profile?.name.orEmpty()) }
    var includePower by remember(key) { mutableStateOf(profile?.power != null || fromState) }
    var power by remember(key) { mutableStateOf(profile?.power ?: initialState?.power ?: true) }
    var includeMode by remember(key) { mutableStateOf(profile?.mode != null || fromState) }
    var mode by remember(key) { mutableStateOf(profile?.mode ?: initialState?.mode?.name ?: "HEAT") }
    var includeTemp by remember(key) { mutableStateOf(profile?.targetTemp != null || fromState) }
    var temp by remember(key) {
        mutableStateOf(((profile?.targetTemp ?: initialState?.presetTemp ?: 21.0) * 2).let { Math.round(it) / 2.0 }.coerceIn(16.0, 30.0))
    }
    var includeFan by remember(key) { mutableStateOf(profile?.fanSpeed != null || fromState) }
    var fan by remember(key) { mutableIntStateOf((profile?.fanSpeed ?: initialState?.airFlow ?: 0).coerceIn(0, 4)) }
    var includeVertical by remember(key) { mutableStateOf(profile?.verticalSwing != null || fromState) }
    var vertical by remember(key) { mutableIntStateOf((profile?.verticalSwing ?: initialState?.windUD ?: 0).coerceIn(0, 4)) }
    val vaneLR = modelSupportsVaneLR != false
    var includeHorizontal by remember(key) { mutableStateOf(vaneLR && (profile?.horizontalSwing != null || fromState)) }
    var horizontal by remember(key) { mutableIntStateOf((profile?.horizontalSwing ?: initialState?.windLR ?: 0).coerceIn(0, 7)) }
    var include3d by remember(key) { mutableStateOf(vaneLR && (profile?.threeDAuto != null || fromState)) }
    var threeD by remember(key) { mutableStateOf(profile?.threeDAuto ?: initialState?.entrust ?: false) }
    var showInfo by remember { mutableStateOf(false) }
    // Until chosen by hand, a profile without an assignment follows its mode (heating = winter, cooling = summer).
    var seasonChoice by remember(key) { mutableStateOf(profile?.season?.takeIf { it in ProfileSeason.VALUES }) }
    val season = seasonChoice ?: if (includeMode) ProfileSeason.fromMode(mode) else if (profile == null) currentSeason else ProfileSeason.ALL

    fun build() = ClimateProfile(
        id = profile?.id ?: UUID.randomUUID().toString(),
        deviceId = deviceId, name = name.trim(),
        power = power.takeIf { includePower },
        mode = mode.takeIf { includeMode },
        targetTemp = temp.takeIf { includeTemp },
        fanSpeed = fan.takeIf { includeFan },
        verticalSwing = vertical.takeIf { includeVertical },
        horizontalSwing = horizontal.takeIf { vaneLR && includeHorizontal },
        threeDAuto = threeD.takeIf { vaneLR && include3d },
        updatedAt = System.currentTimeMillis(),
        season = season
    )

    val anySetting = includePower || includeMode || includeTemp || includeFan || includeVertical ||
        (vaneLR && (includeHorizontal || include3d))
    val valid = name.isNotBlank() && anySetting
    val preview = build().summary().ifBlank { "Noch nichts festgelegt" }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DialogNavigationBar(SPage)
        Surface(Modifier.fillMaxSize(), color = SPage) {
          Column(Modifier.fillMaxSize()) {
            // Fixed header: close and save stay reachable while scrolling.
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("✕", color = SText, fontSize = 18.sp)
                }
                Text(
                    if (profile == null) "Neues Profil" else "Profil", color = SText, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onSave(build()) }, enabled = valid) {
                    Text("Sichern", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = if (valid) SBlue else SMuted)
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // Hero: the name, editable in place, with what the profile does at a glance.
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF1E7BC4), Color(0xFF12558C))))
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Profilname", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Box {
                            if (name.isEmpty()) Text("z. B. Nachtmodus", color = Color.White.copy(alpha = 0.45f), fontSize = 28.sp, fontWeight = FontWeight.Light)
                            BasicTextField(
                                value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                                textStyle = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light),
                                cursorBrush = SolidColor(Color.White),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text(preview, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }

                Group("Jahreszeit") {
                    val options = listOf(ProfileSeason.WINTER to "☼ Winter", ProfileSeason.SUMMER to "❄ Sommer", ProfileSeason.ALL to "Ganzjährig")
                    Segmented(options.map { it.second }, options.indexOfFirst { it.first == season }.coerceAtLeast(0), Modifier.padding(vertical = 10.dp)) {
                        seasonChoice = options[it].first
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("WAS DAS PROFIL EINSTELLT", color = SMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        Box(Modifier.padding(start = 6.dp).size(22.dp).clip(CircleShape).clickable { showInfo = true }, contentAlignment = Alignment.Center) {
                            Text("ⓘ", color = SMuted, fontSize = 14.sp)
                        }
                    }
                    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                            Toggle("Ein / Aus", includePower) { includePower = it }
                            AnimatedVisibility(includePower) {
                                Segmented(listOf("Einschalten", "Ausschalten"), if (power) 0 else 1, Modifier.padding(bottom = 10.dp)) { power = it == 0 }
                            }
                            RowDivider()
                            Toggle("Modus", includeMode) { includeMode = it }
                            AnimatedVisibility(includeMode) {
                                FlowRow(Modifier.padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("AUTO" to "✦ Auto", "COOL" to "❄ Kühlen", "HEAT" to "☼ Heizen", "FAN" to "≈ Lüfter", "DRY" to "◇ Trocknen").forEach { (value, label) ->
                                        Chip(label, mode == value) { mode = value }
                                    }
                                }
                            }
                            RowDivider()
                            Toggle("Temperatur", includeTemp) { includeTemp = it }
                            AnimatedVisibility(includeTemp) {
                                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    MiniRound("−") { temp = (temp - 0.5).coerceAtLeast(16.0) }
                                    Text(
                                        "%.1f °C".format(Locale.GERMANY, temp), color = SText, fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.width(110.dp)
                                    )
                                    MiniRound("+") { temp = (temp + 0.5).coerceAtMost(30.0) }
                                }
                            }
                            RowDivider()
                            Toggle("Lüfter", includeFan, value = ScheduleMath.fanLabels.getOrElse(fan) { "–" }.takeIf { includeFan }) { includeFan = it }
                            AnimatedVisibility(includeFan) {
                                Slider(
                                    value = fan.toFloat(), onValueChange = { fan = Math.round(it) },
                                    valueRange = 0f..4f, steps = 3, modifier = Modifier.padding(bottom = 4.dp),
                                    colors = blueSliderColors()
                                )
                            }
                            RowDivider()
                            Toggle("Vertikal", includeVertical) { includeVertical = it }
                            AnimatedVisibility(includeVertical) {
                                SettingRow("Richtung") {
                                    Dropdown(listOf("Auto", "Ganz oben", "Mitte oben", "Mitte unten", "Ganz unten"), vertical) { vertical = it }
                                }
                            }
                            if (vaneLR) {
                                RowDivider()
                                Toggle("Horizontal", includeHorizontal) { includeHorizontal = it }
                                AnimatedVisibility(includeHorizontal) {
                                    SettingRow("Richtung") {
                                        Dropdown(listOf("Auto", "Links außen", "Links", "Mitte links", "Mitte", "Mitte rechts", "Rechts", "Rechts außen"), horizontal) { horizontal = it }
                                    }
                                }
                                RowDivider()
                                Toggle("3D-Auto-Luftstrom", include3d) { include3d = it }
                                AnimatedVisibility(include3d) {
                                    Segmented(listOf("Ein", "Aus"), if (threeD) 0 else 1, Modifier.padding(bottom = 10.dp)) { threeD = it == 0 }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(40.dp))
            }
          }
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
            title = { Text("Was das Profil einstellt") },
            text = {
                Text("Eingeschaltete Werte setzt das Profil beim Aktivieren. Ausgeschaltete Werte lässt es so, wie sie an der Anlage gerade sind – z. B. ein Profil „Leise“, das nur den Lüfter ändert.")
            }
        )
    }
}

/** Setting header: the switch decides whether the profile sets this value. */
@Composable
private fun Toggle(label: String, checked: Boolean, value: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp).clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (checked) SText else SMuted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        when {
            value != null -> Text(value, color = SBlue, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 10.dp))
            !checked -> Text("unverändert", color = SMuted, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
        }
        Switch(checked, onChange, colors = blueSwitchColors())
    }
}
