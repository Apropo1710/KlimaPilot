package de.klimapilot.app

import android.content.Context
import de.klimapilot.app.wfrac.AirconState
import de.klimapilot.app.wfrac.DemoAirconClient
import de.klimapilot.app.wfrac.Mode
import de.klimapilot.app.wfrac.WfRacControlRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One control entry point for real WF-RAC units and isolated in-memory demo units. */
internal class ClimateDeviceController(context: Context) {
    private val real = WfRacControlRepository(context.applicationContext)
    private val demos = ConcurrentHashMap<String, DemoAirconClient>()
    private val demoLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun read(device: UserDevice): AirconState {
        if (device.demoMode) {
            val client = demos.computeIfAbsent(device.id) { DemoAirconClient() }
            return client.getStatus(device.id).state
        }
        val airconId = device.wfRacAirconId.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Für dieses Gerät ist noch kein WLAN-Modul gekoppelt.")
        ensurePaired(device, airconId)
        return real.readStatus(airconId).status.state
    }

    /**
     * A device can still name its airconId while the module identity behind it is gone
     * (e.g. the same unit was assigned twice and one assignment was reset). Restore it from
     * the stored host when the module there still reports the same airconId.
     */
    private suspend fun ensurePaired(device: UserDevice, airconId: String) {
        if (real.identities.findByAirconId(airconId) != null) return
        val host = device.wfRacHost.trim()
        if (host.isBlank()) {
            throw IllegalStateException("Die Kopplung dieser Klimaanlage fehlt. Bitte in den Geräteeinstellungen „WLAN-Modul koppeln und auslesen“ ausführen.")
        }
        real.restore(host, airconId)
    }

    suspend fun apply(device: UserDevice, change: (AirconState) -> AirconState): AirconState {
        if (device.demoMode) {
            val client = demos.computeIfAbsent(device.id) { DemoAirconClient() }
            return demoLocks.computeIfAbsent(device.id) { Mutex() }.withLock {
                client.registerAccount(device.id, java.util.TimeZone.getDefault().id)
                val current = client.getStatus(device.id).state
                client.setStat(device.id, change(current))
            }
        }
        val airconId = device.wfRacAirconId.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Für dieses Gerät ist noch kein WLAN-Modul gekoppelt.")
        ensurePaired(device, airconId)
        return real.apply(airconId, waitUpToS = 0, change = change)
    }

    fun forget(airconId: String): Boolean = real.forget(airconId)
}
