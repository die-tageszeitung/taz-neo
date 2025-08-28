package de.taz.app.android.scrubber

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.taz.app.android.SCRUBBER_INTERVAL_DAYS
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.util.Log
import java.util.concurrent.TimeUnit

private const val SCRUBBER_WORK_NAME = "ScrubberWorker"

fun enqueueScrubberWorker(applicationContext: Context) {
    val constraints = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Constraints.Builder().setRequiresDeviceIdle(true).build()
    } else {
        Constraints.NONE
    }

    val scrubberWorkRequest =
        PeriodicWorkRequestBuilder<ScrubberWorker>(SCRUBBER_INTERVAL_DAYS, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

    val workManager = WorkManager.getInstance(applicationContext)
    workManager.enqueueUniquePeriodicWork(
        SCRUBBER_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, scrubberWorkRequest
    )
}

class ScrubberWorker(applicationContext: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(applicationContext, workerParameters) {

    private val log by Log

    override suspend fun doWork(): Result {

        val scrubber = Scrubber(applicationContext)
        return try {
            scrubber.scrub()
            Result.success()

        } catch (e: Exception) {
            val message = "Scrubber failed"
            log.warn(message, e)
            SentryWrapper.captureMessage(message)

            Result.failure()
        }
    }
}