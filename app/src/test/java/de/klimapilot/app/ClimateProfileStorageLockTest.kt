package de.klimapilot.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClimateProfileStorageLockTest {
    @Test
    fun repositoryLoadWaitsForSharedStorageCriticalSection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ClimateProfileRepository(context)
        val entered = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val finished = AtomicBoolean(false)

        val holder = Thread {
            synchronized(ClimateProfileStorageLock.monitor) {
                entered.countDown()
                check(completed.await(5, TimeUnit.SECONDS))
            }
        }
        val reader = Thread {
            repository.load()
            finished.set(true)
        }

        holder.start()
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        reader.start()
        Thread.sleep(100)
        assertFalse("Repository load must block while another thread owns the shared lock.", finished.get())

        completed.countDown()
        holder.join(5_000)
        reader.join(5_000)
        assertTrue(finished.get())
    }
}
