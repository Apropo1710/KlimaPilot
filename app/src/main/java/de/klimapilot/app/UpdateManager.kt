package de.klimapilot.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE_API = "https://api.github.com/repos/Apropo1710/KlimaPilot/releases/tags/latest"

private data class KlimaUpdate(val version: String, val downloadUrl: String, val size: Long)

@Composable
internal fun UpdateManagerCard(appVersion: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Suche nach einer neuen Version …") }
    var latest by remember { mutableStateOf<KlimaUpdate?>(null) }
    var checked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun checkForUpdate() {
        if (checking || downloading) return
        checking = true
        error = null
        scope.launch {
            try {
                latest = withContext(Dispatchers.IO) {
                    val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
                    connection.connectTimeout = 12000
                    connection.readTimeout = 12000
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                    connection.setRequestProperty("User-Agent", "KlimaPilot-Android")
                    try {
                        val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                        val version = Regex("KlimaPilot-Version:\\s*([0-9.]+)").find(json.optString("body"))?.groupValues?.get(1)
                            ?: throw IllegalStateException("Versionsnummer im Release fehlt")
                        val assets = json.getJSONArray("assets")
                        var apkUrl: String? = null
                        var apkSize = 0L
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.optString("name") == "app-release.apk") {
                                apkUrl = asset.optString("browser_download_url")
                                apkSize = asset.optLong("size")
                            }
                        }
                        KlimaUpdate(version, apkUrl ?: throw IllegalStateException("APK im Release nicht gefunden"), apkSize)
                    } finally {
                        connection.disconnect()
                    }
                }
                status = if (compareVersions(latest!!.version, appVersion) > 0) "Version ${latest!!.version} ist verfügbar" else "Du verwendest bereits die aktuelle Version"
                checked = true
            } catch (t: Throwable) {
                error = t.message ?: "Update-Prüfung fehlgeschlagen"
                status = "Update-Prüfung nicht möglich"
                checked = true
            } finally {
                checking = false
            }
        }
    }

    fun installDownloaded(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            status = "Bitte Installation aus dieser Quelle erlauben und danach erneut auf Installieren tippen."
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    fun downloadAndInstall() {
        val release = latest ?: return
        if (downloading) return
        downloading = true
        progress = 0f
        error = null
        status = "Update wird heruntergeladen …"
        scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    val connection = URL(release.downloadUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    connection.instanceFollowRedirects = true
                    connection.setRequestProperty("User-Agent", "KlimaPilot-Android")
                    val target = File(context.cacheDir, "klimapilot-update.apk")
                    try {
                        val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.size
                        connection.inputStream.use { input ->
                            target.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var received = 0L
                                var lastUpdate = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    if (count == 0) continue
                                    output.write(buffer, 0, count)
                                    received += count
                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdate > 180) {
                                        val value = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else 0f
                                        withContext(Dispatchers.Main) { progress = value }
                                        lastUpdate = now
                                    }
                                }
                            }
                        }
                    } finally {
                        connection.disconnect()
                    }
                    if (!target.exists() || target.length() < 100_000L) throw IllegalStateException("Der APK-Download ist unvollständig")
                    target
                }
                progress = 1f
                status = "Download abgeschlossen. Android-Installation wird geöffnet …"
                installDownloaded(apk)
            } catch (t: Throwable) {
                error = t.message ?: "Download fehlgeschlagen"
                status = "Update konnte nicht heruntergeladen werden"
            } finally {
                downloading = false
            }
        }
    }

    LaunchedEffect(Unit) { checkForUpdate() }

    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.SystemUpdate, tint = Color(0xFF1769AA), modifier = Modifier.size(25.dp), contentDescription = null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("App-Update", color = Color(0xFF172334), fontWeight = FontWeight.SemiBold)
                    Text("Installiert: $appVersion", color = Color(0xFF63748A), fontSize = 12.sp)
                }
                if (checked && latest != null && compareVersions(latest!!.version, appVersion) <= 0) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Aktuell", tint = Color(0xFF16845B))
                }
            }
            Text(status, color = Color(0xFF63748A), fontSize = 13.sp)
            if (downloading) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Color(0xFF1769AA))
                Text("${(progress * 100).toInt()} %", color = Color(0xFF63748A), fontSize = 12.sp)
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { checkForUpdate() }, enabled = !checking && !downloading, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (checking) "Prüfe …" else "Prüfen")
                }
                Button(
                    onClick = { if (latest == null) checkForUpdate() else downloadAndInstall() },
                    enabled = !checking && !downloading && latest != null && compareVersions(latest!!.version, appVersion) > 0,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA))
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Update laden")
                }
            }
            Text("Die Installation wird anschließend von Android bestätigt.", color = Color(0xFF63748A), fontSize = 11.sp)
        }
    }
}

private fun compareVersions(left: String, right: String): Int {
    val a = left.split(".").map { it.toIntOrNull() ?: 0 }
    val b = right.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val difference = (a.getOrElse(i) { 0 }).compareTo(b.getOrElse(i) { 0 })
        if (difference != 0) return difference
    }
    return 0
}
