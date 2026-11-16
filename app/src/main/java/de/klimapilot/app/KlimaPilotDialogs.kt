package de.klimapilot.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Einheitlicher, dezent blau hinterlegter Dialograhmen für alle App-Dialoge.
 * Die übergebenen Aktions-Slots behalten ihre jeweilige Fachlogik unverändert.
 */
@Composable
internal fun KlimaPilotAlertDialog(
    onDismissRequest: () -> Unit,
    containerColor: Color = Color(0xFFEAF3FC),
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(26.dp)
    val accent = MaterialTheme.colorScheme.primary
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.fillMaxWidth().border(2.5.dp, Color(0xFF1769AA), shape),
            shape = shape,
            color = containerColor,
            tonalElevation = 8.dp,
            shadowElevation = 10.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ProvideDialogTitle { title() }
                ProvideDialogText { text() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
                            .background(Color(0xFFCFE3F7), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) { confirmButton() }
                    if (dismissButton != null) {
                        Box(
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
                                .background(Color(0xFFDCE6F0), RoundedCornerShape(13.dp)),
                            contentAlignment = Alignment.Center
                        ) { dismissButton() }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProvideDialogTitle(content: @Composable () -> Unit) {
    androidx.compose.material3.ProvideTextStyle(
        MaterialTheme.typography.headlineSmall.copy(
            color = Color(0xFF172B45),
            fontWeight = FontWeight.SemiBold
        ),
        content = content
    )
}

@Composable
private fun ProvideDialogText(content: @Composable () -> Unit) {
    androidx.compose.material3.ProvideTextStyle(
        MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF52657B)),
        content = content
    )
}
