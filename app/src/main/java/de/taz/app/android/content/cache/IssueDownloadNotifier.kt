package de.taz.app.android.content.cache

import android.content.Context
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.WidgetHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.Log
import java.util.Date

/**
 * A helper class that can be used to notify the API of the progress of an issue download
 * @param issueKey The [AbstractIssueKey] of which the progress should be notified
 * @param isAutomaticDownload Indicating if the download was triggered by a user or automatically (By scheduler)
 */
class IssueDownloadNotifier(
    private val applicationContext: Context,
    private val issueKey: AbstractIssueKey,
    private val isAutomaticDownload: Boolean
) {
    private val apiService = ApiService.getInstance(applicationContext)
    private val tracker = Tracker.getInstance(applicationContext)

    private val log by Log
    private lateinit var started: Date
    private var downloadId: String? = null


    /**
     * Indicate that a download progress of [issueKey] has been started
     * Always fails silently to avoid important stuff to be interrupted.
     */
    suspend fun start() {
        try {
            notifyIssueDownloadStart()
        } catch (e: Exception) {
            log.warn("Error while notifying download start for $issueKey", e)
            SentryWrapper.captureException(e)
        }
    }

    /**
     * Indicate that a download progress of [issueKey] has been stopped.
     * Should only be executed if this [IssueDownloadNotifier] was already [start]ed
     * Always fails silently to avoid important stuff to be interrupted.
     */
    suspend fun stop() {
        try {
            notifyIssueDownloadStop()
            WidgetHelper.updateWidget(applicationContext)
        } catch (e: Exception) {
            log.warn("Error while notifying download stop for $issueKey",e)
            SentryWrapper.captureException(e)
        }
    }

    /**
     * This function get a [downloadId] and saves the current time
     */
    private suspend fun notifyIssueDownloadStart() {
        try {
            started = Date()
            downloadId = apiService.notifyServerOfDownloadStart(
                issueKey.feedName,
                issueKey.date,
                isAutomaticDownload
            ) ?: throw ConnectivityException.ImplementationException("No download id in response")
        } catch (nie: ConnectivityException) {
            // do nothing
        }
    }

    /**
     * Calculates the passed time and notifies the API of download completion
     */
    private suspend fun notifyIssueDownloadStop() {
        val secondsTaken = (Date().time - started.time).toFloat() / 1000

        downloadId?.let {
            apiService.notifyServerOfDownloadStop(
                it, secondsTaken
            )
            tracker.trackIssueDownloadEvent(issueKey)
            log.debug("Issue download of $issueKey completed after $secondsTaken")
        } ?: log.warn("Somehow download Id was null so information of downloadStop failed!")
    }
}