package de.klimapilot.app.wfrac

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory WF-RAC simulator for exercising the UI and control flow without installed hardware.
 * Every write is passed through the production frame encoder/decoder so unsupported values
 * behave like a real module. No network traffic is made and state resets when the process ends.
 */
class DemoAirconClient(private val now: () -> Long = System::currentTimeMillis) : AirconClient {
    override val scheme: Scheme? get() = null
    override val certificatePin: String? get() = null

    private val mutex = Mutex()
    private var registered = false
    private var state = roundTrip(
        AirconState(
            power = false,
            mode = Mode.COOL,
            presetTemp = 21.0,
            airFlow = 0,
            windUD = 0,
            windLR = 4,
            entrust = false,
            coolHotJudge = true,
            modelNr = 0,
            modelNrRaw = 0,
            vacant = false,
            selfClean = false,
        )
    )
    private var indoor = ROOM_TEMP
    private var indoorAt = now()

    override suspend fun getStatus(airconId: String): UnitStatus =
        mutex.withLock { UnitStatus(reported(), accounts = if (registered) 1 else 0) }

    override suspend fun setStat(airconId: String, state: AirconState): AirconState = mutex.withLock {
        if (!registered) throw WfRacException(WfRacException.UNIT_REFUSED, "Demo-Modul verweigert Steuerung ohne Registrierung.", 2)
        advanceIndoor()
        this.state = roundTrip(state)
        reported()
    }

    override suspend fun registerAccount(airconId: String, timezone: String) {
        mutex.withLock { registered = true }
    }

    override suspend fun migrateAccount(airconId: String, legacyOperatorId: String, timezone: String) {
        registerAccount(airconId, timezone)
    }

    private fun reported(): AirconState {
        advanceIndoor()
        return state.copy(
            indoorTemp = (indoor * 2).roundToLong() / 2.0,
            outdoorTemp = OUTDOOR_TEMP,
        )
    }

    private fun advanceIndoor() {
        val time = now()
        val minutes = (time - indoorAt) / 60_000.0
        indoorAt = time
        if (minutes <= 0) return
        val (target, rate) = when {
            state.power && state.mode == Mode.COOL -> min(state.presetTemp, ROOM_TEMP) to RUNNING_RATE
            state.power && state.mode == Mode.HEAT -> max(state.presetTemp, ROOM_TEMP) to RUNNING_RATE
            state.power && state.mode == Mode.AUTO -> state.presetTemp to RUNNING_RATE
            else -> ROOM_TEMP to IDLE_RATE
        }
        val step = min(abs(target - indoor), rate * minutes)
        indoor += if (target > indoor) step else -step
    }

    private companion object {
        const val ROOM_TEMP = 23.5
        const val OUTDOOR_TEMP = 14.5
        const val RUNNING_RATE = 0.2
        const val IDLE_RATE = 0.05

        fun roundTrip(state: AirconState) = Codec.decode(Codec.encode(state))
    }
}
