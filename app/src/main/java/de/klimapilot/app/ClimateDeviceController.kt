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
        return real.readStatus(airconId).status.state
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
        return real.apply(airconId, waitUpToS = 0, change = change)
    }

    fun forget(airconId: String): Boolean = real.forget(airconId)
}
