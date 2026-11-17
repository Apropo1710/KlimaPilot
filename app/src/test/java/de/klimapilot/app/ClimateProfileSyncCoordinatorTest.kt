package de.klimapilot.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ClimateProfileSyncCoordinatorTest {
    @After
    fun releaseCoordinator() {
        ClimateProfileSyncCoordinator.release()
    }

    @Test
    fun onlyOneTransportCanOwnTheSyncCoordinatorAtATime() {
        assertTrue(ClimateProfileSyncCoordinator.tryAcquire())
        assertFalse(ClimateProfileSyncCoordinator.tryAcquire())
        assertTrue(ClimateProfileSyncCoordinator.isRunningForTest())

        ClimateProfileSyncCoordinator.release()

        assertFalse(ClimateProfileSyncCoordinator.isRunningForTest())
        assertTrue(ClimateProfileSyncCoordinator.tryAcquire())
    }

    @Test
    fun concurrentTransportStartsHaveExactlyOneWinner() {
        val executor = Executors.newFixedThreadPool(8)
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val winners = AtomicInteger(0)
        try {
            val tasks = (0 until 8).map {
                executor.submit {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    if (ClimateProfileSyncCoordinator.tryAcquire()) winners.incrementAndGet()
                }
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            start.countDown()
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
            assertTrue(winners.get() == 1)
        } finally {
            ClimateProfileSyncCoordinator.release()
            executor.shutdownNow()
        }
    }
}
