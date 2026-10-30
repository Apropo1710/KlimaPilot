package de.klimapilot.app

import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val LATEST_RELEASE_API = "https://api.github.com/repos/Apropo1710/KlimaPilot/releases/tags/latest"
private data class KlimaUpdate(val version: String, val downloadUrl: String)

@Composable
internal fun UpdateManagerCard(appVersion: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Suche nach einer neuen Version …") }
    var latest by remember { mutableStateOf<KlimaUpdate?>(null) }
    var checked by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun checkForUpdate() {
        if (checking) return
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
                        val responseCode = connection.responseCode
                        val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                            ?.bufferedReader()?.use { it.readText() }.orEmpty()
                        if (responseCode !in 200..299) {
                            throw IllegalStateException("GitHub antwortet mit HTTP $responseCode")
                        }
                        val json = JSONObject(responseText)
                        val releaseBody = json.optString("body")
                        val version = Regex("""KlimaPilot-Version:\s*([0-9.]+)""").find(releaseBody)?.groupValues?.get(1)
                            ?: json.optString("name").let { Regex("""([0-9]+\.[0-9]+\.[0-9]+)""").find(it)?.groupValues?.get(1) }
                            ?: throw IllegalStateException("Versionsnummer fehlt in der Release-Beschreibung")
                        val assets = json.getJSONArray("assets")
                        var apkUrl: String? = null
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.optString("name") == "KlimaPilot.apk") apkUrl = asset.optString("browser_download_url")
                        }
                        KlimaUpdate(version, apkUrl ?: throw IllegalStateException("KlimaPilot.apk wurde im Release nicht gefunden"))
                    } finally {
                        connection.disconnect()
                    }
                }
                status = if (compareVersions(latest!!.version, appVersion) > 0)
                    "Version ${latest!!.version} ist verfügbar"
                else "Du verwendest bereits die aktuelle Version"
                checked = true
            } catch (t: Throwable) {
                error = when (t) {
                    is java.net.UnknownHostException -> "Keine Verbindung zu GitHub. Prüfe Internet oder VPN."
                    is java.net.SocketTimeoutException -> "GitHub antwortet gerade nicht. Bitte erneut versuchen."
                    else -> t.message?.takeIf { it.isNotBlank() } ?: "Update-Prüfung fehlgeschlagen"
                }
                status = "Update-Prüfung nicht möglich"
                checked = true
            } finally {
                checking = false
            }
        }
    }

    fun openDownload() {
        val url = latest?.downloadUrl ?: return
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
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
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { checkForUpdate() }, enabled = !checking, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(if (checking) "Prüfe …" else "Prüfen")
                }
                Button(
                    onClick = { if (latest == null) checkForUpdate() else openDownload() },
                    enabled = !checking && latest != null && compareVersions(latest!!.version, appVersion) > 0,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA))
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Im Browser laden")
                }
            }
            Text("Der Download und die Installation erfolgen anschließend über Android bzw. deinen Browser. KlimaPilot benötigt dafür keine Berechtigung zum Installieren fremder Apps.", color = Color(0xFF63748A), fontSize = 11.sp, lineHeight = 15.sp)
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
