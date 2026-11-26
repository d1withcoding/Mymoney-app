package com.pennywiseai.tracker.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.Operation
import androidx.work.WorkManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Pins what the schedulers hand to WorkManager. WorkManager keeps unique work
 * names, tags and input keys across app updates, so these literals must not
 * change when the schedulers move or are refactored.
 */
class SchedulerWorkRequestsTest {

    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    @Before fun setUp() {
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
    }

    @After fun tearDown() {
        unmockkObject(WorkManager.Companion)
    }

    @Test fun smsScan_replacesUniqueOneTimeWorkWithTheOptimizedReader() {
        val request = slot<OneTimeWorkRequest>()
        val manager = SmsScanManager(context)

        manager.startSmsLoggingScan()

        verify { workManager.enqueueUniqueWork("optimized_sms_reader_work", ExistingWorkPolicy.REPLACE, capture(request)) }
        assertEquals("com.pennywiseai.tracker.worker.OptimizedSmsReaderWorker", request.captured.workSpec.workerClassName)
        assertTrue(request.captured.tags.containsAll(setOf("sms_logging", "optimized_sms_processing")))
        assertEquals(NetworkType.NOT_REQUIRED, request.captured.workSpec.constraints.requiredNetworkType)
    }

    @Test fun smsScan_cancelAndProgressUseTheSameNameAndTag() {
        val manager = SmsScanManager(context)

        manager.cancelSmsScanning()
        manager.getSmsScanWorkInfo()

        verify { workManager.cancelUniqueWork("optimized_sms_reader_work") }
        verify { workManager.getWorkInfosByTagLiveData("optimized_sms_processing") }
    }

}
