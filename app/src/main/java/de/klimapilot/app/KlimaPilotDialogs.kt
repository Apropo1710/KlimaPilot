package de.klimapilot.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

private val DialogBlue = Color(0xFF1769AA)
private val DialogBlueDark = Color(0xFF12558C)
private val DialogTitle = Color(0xFF172B45)
private val DialogText = Color(0xFF52657B)

/**
 * Uniform frame for every dialog in the app: white card with a fine blue border, a blue accent
 * strip at the top and rounded, tinted buttons. Same slots as Material's AlertDialog, so the
 * dialogs' own logic stays unchanged.
 */
@Composable
internal fun KlimaPilotAlertDialog(
    onDismissRequest: () -> Unit,
    containerColor: Color = Color.White,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(28.dp)
    ScaledDialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            // Clear of status and navigation bar, also with long texts.
            modifier = Modifier.fillMaxWidth().systemBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = shape,
            color = containerColor,
            border = BorderStroke(1.5.dp, DialogBlue.copy(alpha = 0.35f)),
            shadowElevation = 16.dp
        ) {
            Column {
                // Accent strip: the blue of the app, as on the main cards.
                Box(Modifier.fillMaxWidth().height(6.dp).background(Brush.horizontalGradient(listOf(DialogBlue, DialogBlueDark, DialogBlue.copy(alpha = 0.6f)))))
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    title?.let {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(color = DialogTitle, fontWeight = FontWeight.SemiBold, fontSize = 21.sp)) {
                            CompositionLocalProvider(LocalContentColor provides DialogTitle) { it() }
                        }
                    }
                    text?.let {
                        // Long texts scroll inside the dialog; title and buttons stay visible.
                        Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                            ProvideTextStyle(MaterialTheme.typography.bodyLarge.copy(color = DialogText, fontSize = 15.sp, lineHeight = 22.sp)) {
                                CompositionLocalProvider(LocalContentColor provides DialogText) { it() }
                            }
                        }
                    }
                    if (confirmButton != null || dismissButton != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            dismissButton?.let {
                                Box(
                                    Modifier.weight(1f).defaultMinSize(minHeight = 46.dp)
                                        .background(Color(0xFFF0F4F8), RoundedCornerShape(50)),
                                    contentAlignment = Alignment.Center
                                ) { it() }
                            }
                            confirmButton?.let {
                                Box(
                                    Modifier.weight(1f).defaultMinSize(minHeight = 46.dp)
                                        .background(DialogBlue.copy(alpha = 0.12f), RoundedCornerShape(50)),
                                    contentAlignment = Alignment.Center
                                ) { it() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A dialog window gets its own density from the system, which drops the font size chosen in the
 * app. This wrapper hands the app's density (including that font size) on to the dialog content.
 */
@Composable
internal fun ScaledDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides density) { content() }
    }
}
