package de.klimapilot.app

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object GithubPrivateUpdates {
    private const val CLIENT_ID = "Iv23liXzp9rdc19pB4qt"
    private const val PREFS = "klimapilot_github"
    private const val KEY_ALIAS = "klimapilot_github_token_key"

    data class Device(val deviceCode: String, val userCode: String, val verificationUri: String, val expiresIn: Int, val interval: Int)
    data class Release(val version: String, val assetId: Long)
    private data class Tokens(val access: String, val refresh: String, val expiresAt: Long)

    fun isConnected(context: Context) = load(context) != null

    fun pendingDevice(context: Context): Device? = runCatching {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("pending", null) ?: return null
        val json = JSONObject(decrypt(stored))
        Device(json.getString("device"), json.getString("user"), json.getString("uri"), json.getInt("expires"), json.getInt("interval"))
    }.getOrNull()

    fun savePending(context: Context, device: Device) {
        val json = JSONObject().put("device", device.deviceCode).put("user", device.userCode)
            .put("uri", device.verificationUri).put("expires", device.expiresIn).put("interval", device.interval)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("pending", encrypt(json.toString())).commit()) {
            "Anmeldestatus konnte nicht gespeichert werden."
        }
    }

    private fun post(url: String, values: Map<String, String>): JSONObject {
        val body = values.entries.joinToString("&") { URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8") }
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.doOutput = true
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val status = c.responseCode
            val stream = if (status in 200..299) c.inputStream else c.errorStream
            val json = JSONObject(stream?.bufferedReader()?.use { it.readText() } ?: "{}")
            if (status !in 200..299 && !json.has("error")) error("GitHub HTTP $status")
            return json
        } finally { c.disconnect() }
    }

    suspend fun begin(): Device = withContext(Dispatchers.IO) {
        val j = post("https://github.com/login/device/code", mapOf("client_id" to CLIENT_ID, "scope" to "offline_access"))
        Device(j.getString("device_code"), j.getString("user_code"), j.getString("verification_uri"), j.optInt("expires_in", 900), j.optInt("interval", 5))
    }

    suspend fun authorize(context: Context, device: Device) = withContext(Dispatchers.IO) {
        val deadline = SystemClock.elapsedRealtime() + device.expiresIn * 1000L
        var interval = device.interval.coerceAtLeast(5)
        while (SystemClock.elapsedRealtime() < deadline) {
            delay(interval * 1000L)
            val j = post("https://github.com/login/oauth/access_token", mapOf(
                "client_id" to CLIENT_ID,
                "device_code" to device.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
            ))
            when (j.optString("error")) {
                "authorization_pending" -> continue
                "slow_down" -> { interval += 5; continue }
                "access_denied" -> error("GitHub-Anmeldung abgelehnt.")
                "expired_token" -> error("GitHub-Code abgelaufen. Bitte erneut verbinden.")
                "" -> {
                    val access = j.optString("access_token")
                    val refresh = j.optString("refresh_token")
                    if (access.isBlank()) error("GitHub hat kein Zugriffstoken geliefert.")
                    save(context, Tokens(access, refresh, System.currentTimeMillis() + j.optLong("expires_in", 28800L) * 1000L))
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("pending").commit()
                    return@withContext
                }
                else -> error(j.optString("error_description", j.optString("error")))
            }
        }
        error("GitHub-Code abgelaufen. Bitte erneut verbinden.")
    }

    private fun accessToken(context: Context): String {
        val old = load(context) ?: error("Bitte zuerst mit GitHub verbinden.")
        if (old.expiresAt > System.currentTimeMillis() + 60000L || old.refresh.isBlank()) return old.access
        val j = post("https://github.com/login/oauth/access_token", mapOf(
            "client_id" to CLIENT_ID,
            "grant_type" to "refresh_token",
            "refresh_token" to old.refresh
        ))
        val access = j.optString("access_token")
        val refresh = j.optString("refresh_token")
        if (access.isBlank()) {
            clear(context)
            error("GitHub-Sitzung abgelaufen. Bitte erneut verbinden.")
        }
        save(context, Tokens(access, refresh, System.currentTimeMillis() + j.optLong("expires_in", 28800L) * 1000L))
        return access
    }

    private fun isWlanTest(context: Context) = context.packageName.endsWith(".debug")
    private fun releaseTag(context: Context) = if (isWlanTest(context)) "wlan-test-latest" else "latest"
    private fun releaseAssetName(context: Context) =
        if (isWlanTest(context)) "KlimaPilot-WLAN-debug.apk" else "KlimaPilot.apk"

    suspend fun latestRelease(context: Context): Release = withContext(Dispatchers.IO) {
        val testBuild = isWlanTest(context)
        val json = get("https://api.github.com/repos/Apropo1710/KlimaPilot/releases/tags/${releaseTag(context)}", accessToken(context))
        val marker = if (testBuild) "KlimaPilot-WLAN-Version:" else "KlimaPilot-Version:"
        val version = Regex(Regex.escape(marker) + "\\s*([0-9]+(?:\\.[0-9]+)*)")
            .find(json.optString("body"))?.groupValues?.get(1) ?: error("Release-Version konnte nicht gelesen werden.")
        val expectedAsset = releaseAssetName(context)
        val assets = json.optJSONArray("assets") ?: error("Keine Release-Dateien gefunden.")
        var assetId = -1L
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == expectedAsset) { assetId = asset.getLong("id"); break }
        }
        if (assetId < 0) error("$expectedAsset fehlt im Release ${releaseTag(context)}.")
        Release(version, assetId)
    }

    suspend fun download(context: Context, assetId: Long): File = withContext(Dispatchers.IO) {
        val c = URL("https://api.github.com/repos/Apropo1710/KlimaPilot/releases/assets/$assetId").openConnection() as HttpURLConnection
        c.connectTimeout = 20000
        c.readTimeout = 60000
        c.instanceFollowRedirects = true
        c.setRequestProperty("Accept", "application/octet-stream")
        c.setRequestProperty("Authorization", "Bearer " + accessToken(context))
        try {
            if (c.responseCode !in 200..299) error("APK-Download fehlgeschlagen (HTTP ${c.responseCode}).")
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, releaseAssetName(context))
            c.inputStream.use { input -> file.outputStream().use { output -> input.copyTo(output) } }
            if (file.length() < 1000000L) { file.delete(); error("APK-Download ist unvollständig.") }
            file
        } finally { c.disconnect() }
    }

    private fun get(url: String, token: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("Authorization", "Bearer $token")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        try {
            val status = c.responseCode
            val stream = if (status in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            if (status !in 200..299) error("GitHub-API HTTP $status: " + JSONObject(body).optString("message"))
            return JSONObject(body)
        } finally { c.disconnect() }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }

    private fun save(context: Context, tokens: Tokens) {
        val json = JSONObject().put("access", tokens.access).put("refresh", tokens.refresh).put("expires", tokens.expiresAt)
        check(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("tokens", encrypt(json.toString())).commit()) {
            "GitHub-Zugang konnte nicht dauerhaft gespeichert werden."
        }
    }

    private fun load(context: Context): Tokens? = runCatching {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tokens", null) ?: return null
        val json = JSONObject(decrypt(value))
        Tokens(json.getString("access"), json.optString("refresh"), json.getLong("expires"))
    }.getOrNull()

    private fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("tokens").apply()
    }
}