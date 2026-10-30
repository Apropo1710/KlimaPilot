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
                val pattern = Uri.encode("system.config.*")
                connection = (URL("$base/objects?pattern=$pattern").openConnection() as HttpURLConnection).apply {
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
                val objects = JSONObject(body)
                val keys = objects.keys()
                var foundSystemConfig = false
                while (keys.hasNext()) {
                    if (keys.next().startsWith("system.config.")) {
                        foundSystemConfig = true
                        break
                    }
                }
                if (!foundSystemConfig) {
                    throw IllegalStateException("Simple API antwortet, aber ioBroker-Systemobjekte wurden nicht gefunden.")
                }
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
