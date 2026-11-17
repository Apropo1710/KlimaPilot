package de.klimapilot.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ClimateProfileStorageLockTest {
    @Test
    fun sharedMonitorSerializesConcurrentCallers() {
        val entered = AtomicInteger()
        val maximum = AtomicInteger()
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val done = CountDownLatch(8)
        repeat(8) {
            Thread {
                ready.countDown()
                start.await()
                synchronized(ClimateProfileStorageLock.monitor) {
                    val current = entered.incrementAndGet()
                    maximum.updateAndGet { previous -> maxOf(previous, current) }
                    Thread.sleep(10)
                    entered.decrementAndGet()
                }
                done.countDown()
            }.start()
        }
        check(ready.await(5, TimeUnit.SECONDS))
        start.countDown()
        check(done.await(5, TimeUnit.SECONDS))
        assertEquals(1, maximum.get())
    }

    @Test
    fun sharedMonitorIsReentrant() {
        val completed = synchronized(ClimateProfileStorageLock.monitor) {
            synchronized(ClimateProfileStorageLock.monitor) { true }
        }
        assertEquals(true, completed)
    }
}
