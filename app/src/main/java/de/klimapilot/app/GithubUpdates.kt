package de.klimapilot.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates come from the public GitHub release "latest" of Apropo1710/KlimaPilot. The repository
 * is public, so no GitHub sign-in is needed (up to 60 requests per hour per internet address).
 */
internal object GithubUpdates {
    data class Release(val version: String, val assetId: Long)

    /** Removes what the former GitHub sign-in left on the phone (tokens and their key). */
    fun forgetOldSignIn(context: Context) {
        runCatching {
            val prefs = context.getSharedPreferences("klimapilot_github", Context.MODE_PRIVATE)
            if (prefs.all.isNotEmpty()) prefs.edit().clear().apply()
        }
        runCatching {
            val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias("klimapilot_github_token_key")) store.deleteEntry("klimapilot_github_token_key")
        }
    }

    suspend fun latestRelease(context: Context): Release = withContext(Dispatchers.IO) {
        forgetOldSignIn(context)
        val json = get("https://api.github.com/repos/Apropo1710/KlimaPilot/releases/tags/latest")
        val version = Regex("KlimaPilot-Version:\\s*([0-9]+(?:\\.[0-9]+)*)")
            .find(json.optString("body"))?.groupValues?.get(1) ?: error("Release-Version konnte nicht gelesen werden.")
        val assets = json.optJSONArray("assets") ?: error("Keine Release-Dateien gefunden.")
        var assetId = -1L
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == "KlimaPilot.apk") { assetId = asset.getLong("id"); break }
        }
        if (assetId < 0) error("KlimaPilot.apk fehlt im Release.")
        Release(version, assetId)
    }

    suspend fun download(context: Context, assetId: Long): File = withContext(Dispatchers.IO) {
        val c = URL("https://api.github.com/repos/Apropo1710/KlimaPilot/releases/assets/$assetId").openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.instanceFollowRedirects = true
        c.setRequestProperty("Accept", "application/octet-stream")
        try {
            if (c.responseCode !in 200..299) error("APK-Download fehlgeschlagen (HTTP ${c.responseCode}).")
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "KlimaPilot.apk")
            c.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            if (file.length() < 1000000L) { file.delete(); error("APK-Download ist unvollständig.") }
            file
        } finally { c.disconnect() }
    }

    private fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        try {
            val status = c.responseCode
            val stream = if (status in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            if (status !in 200..299) error("GitHub-API HTTP $status: " + runCatching { JSONObject(body).optString("message") }.getOrDefault(""))
            return JSONObject(body)
        } finally { c.disconnect() }
    }
}
