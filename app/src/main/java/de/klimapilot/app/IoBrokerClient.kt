package de.klimapilot.app

import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal object IoBrokerClient {
    private val mainHandler = Handler(Looper.getMainLooper())

    fun testConnection(serverUrl: String, callback: (Boolean, String) -> Unit) {
        Thread {
            var connection: HttpURLConnection? = null
            try {
                val base = serverUrl.trim().trimEnd('/')
                require(base.startsWith("http://") || base.startsWith("https://")) {
                    "Serveradresse muss mit http:// oder https:// beginnen."
                }
                // Match HomePilot: Simple API's /get endpoint returns the state wrapper
                // for ioBroker's own admin adapter, independent of any climate device.
                val stateId = Uri.encode("system.adapter.admin.0.alive")
                connection = (URL("$base/get/$stateId").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 7000
                    readTimeout = 7000
                    setRequestProperty("Accept", "application/json")
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    throw IllegalStateException("HTTP $code${if (body.isNotBlank()) ": $body" else ""}")
                }
                val json = JSONObject(body)
                val state = json.optJSONObject("state")
                val value = when {
                    json.has("val") -> json.opt("val")
                    state?.has("val") == true -> state.opt("val")
                    else -> null
                }
                val alive = when (value) {
                    is Boolean -> value
                    is String -> value.equals("true", ignoreCase = true)
                    else -> false
                }
                if (!alive) throw IllegalStateException("ioBroker antwortet, aber der Admin-Adapter meldet sich nicht als aktiv.")
                mainHandler.post { callback(true, "Verbindung erfolgreich hergestellt") }
            } catch (error: Exception) {
                val message = error.message ?: error.javaClass.simpleName
                mainHandler.post { callback(false, "Verbindung fehlgeschlagen: $message") }
            } finally {
                connection?.disconnect()
            }
        }.start()
    }
}
