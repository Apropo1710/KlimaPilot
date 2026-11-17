package de.klimapilot.app

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide gate shared by every synchronization transport.
 *
 * ioBroker and future paired-phone transports must acquire this same gate before
 * reading, applying, uploading, or acknowledging events in the shared outbox.
 */
internal object ClimateProfileSyncCoordinator {
    private val running = AtomicBoolean(false)

    fun tryAcquire(): Boolean = running.compareAndSet(false, true)

    fun release() {
        running.set(false)
    }

    internal fun isRunningForTest(): Boolean = running.get()
}
