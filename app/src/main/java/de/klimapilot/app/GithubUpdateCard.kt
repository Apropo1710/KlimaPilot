package de.klimapilot.app

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch

@Composable
internal fun GithubUpdateCard(appVersion: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("Suche nach Updates …") }
    var newerVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var latestVersion by rememberSaveable { mutableStateOf<String?>(null) }
    var assetId by rememberSaveable { mutableStateOf<Long?>(null) }

    suspend fun checkForUpdates() {
        busy = true
        status = "Suche nach Updates …"
        try {
            val release = GithubUpdates.latestRelease(context)
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

    LaunchedEffect(Unit) {
        checkForUpdates()
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("App-Version", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            val isCurrentVersion = status == "Du verwendest bereits die aktuelle Version."
            val isUpdateAvailable = newerVersion != null
            Text(
                status,
                color = if (isCurrentVersion || isUpdateAvailable) androidx.compose.ui.graphics.Color(0xFF218838) else MaterialTheme.colorScheme.onSurfaceVariant,
                style = if (isCurrentVersion || isUpdateAvailable) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (isCurrentVersion || isUpdateAvailable) FontWeight.Normal else FontWeight.Normal,
                fontSize = 12.sp
            )
            Text("Installierte Version: $appVersion", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp)
            if (latestVersion != null) {
                Text("Verfügbare Version: $latestVersion", color = if (newerVersion != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, fontWeight = if (newerVersion != null) FontWeight.SemiBold else FontWeight.Normal, fontSize = 12.sp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    onClick = { scope.launch { checkForUpdates() } }
                ) { Text("Nach Updates suchen", maxLines = 1, softWrap = false, fontSize = 13.sp) }
                if (newerVersion != null && assetId != null) {
                    Button(
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        onClick = {
                            scope.launch {
                                busy = true
                                status = "APK wird sicher heruntergeladen …"
                                try {
                                    val file = GithubUpdates.download(context, assetId!!)
                                    status = "Download abgeschlossen. APK wird geprüft …"
                                    val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                                        ?: error("Die heruntergeladene Datei ist keine gültige Android-APK.")
                                    if (archive.packageName != context.packageName) {
                                        error("Die APK gehört zu einer anderen App: " + archive.packageName)
                                    }
                                    val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/vnd.android.package-archive")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                        clipData = ClipData.newUri(context.contentResolver, "KlimaPilot APK", uri)
                                    }
                                    status = "APK geprüft. Android-Installationsdialog wird geöffnet …"
                                    context.startActivity(intent)
                                    status = "Android-Installationsdialog wurde angefordert. Bitte die Installation dort bestätigen."
                                } catch (error: Exception) {
                                    status = "Update fehlgeschlagen: " + (error.message ?: error.javaClass.simpleName)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) { Text("Update laden", maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelLarge, fontSize = 13.sp) }
                }
            }
            if (busy) {
                Spacer(Modifier.height(2.dp))
                Text("Bitte einen Moment warten …", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}