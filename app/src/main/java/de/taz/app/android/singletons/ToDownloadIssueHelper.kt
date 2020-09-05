package de.taz.app.android.singletons

import android.content.Context
import android.content.SharedPreferences
import de.taz.app.android.api.ApiService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.Log
import de.taz.app.android.util.SharedPreferenceStringLiveData
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

const val SHARED_PREFERENCES_GAP_TO_DOWNLOAD = "shared_preferences_gap_to_download"
const val LAST_DOWNLOADED_DATE = "shared_preferences_last_downloaded_date"
const val DATE_TO_DOWNLOAD_FROM = "shared_preferences_date_to_download_from"

/**
 * Singleton to keep track of issueStubs that needs to be downloaded.
 */
class ToDownloadIssueHelper private constructor(applicationContext: Context) {

    companion object : SingletonHolder<ToDownloadIssueHelper, Context>(::ToDownloadIssueHelper)

    private val log by Log

    private val issueRepository = IssueRepository.getInstance(applicationContext)
    private val feedRepository = FeedRepository.getInstance(applicationContext)
    private val apiService = ApiService.getInstance(applicationContext)
    private val downloadService = DownloadService.getInstance(applicationContext)

    private var prefs: SharedPreferences = applicationContext.getSharedPreferences(
        SHARED_PREFERENCES_GAP_TO_DOWNLOAD, Context.MODE_PRIVATE
    )

    private val lastDownloadedDateLiveData =
        SharedPreferenceStringLiveData(prefs, LAST_DOWNLOADED_DATE, "")
    private val dateToDownloadFromLiveData =
        SharedPreferenceStringLiveData(prefs, DATE_TO_DOWNLOAD_FROM, "")

    private var downloadingJob: Job? = null
    private val isDownloading = AtomicBoolean(false)

    init {
        startMissingDownloads()
    }

    private fun startMissingDownloads() {
        if (!isDownloading.getAndSet(true)) {
            downloadingJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    if(lastDownloadedDateLiveData.value.isEmpty()) {
                        val latestIssueDate = issueRepository.getLatestIssue()?.date ?: ""
                        withContext(Dispatchers.Main) {
                            lastDownloadedDateLiveData.value = latestIssueDate
                        }
                    }

                    while (
                        dateToDownloadFromLiveData.value.isNotEmpty() &&
                        lastDownloadedDateLiveData.value.isNotEmpty() &&
                        DateHelper.dayDelta(
                            dateToDownloadFromLiveData.value,
                            lastDownloadedDateLiveData.value
                        ).toInt() > 0
                    ) {
                        if (downloadService.isDownloading()) {
                            delay(1000)
                        } else {
                            try {
                                val missingIssues =
                                    apiService.getIssuesByDateAsync(
                                        lastDownloadedDateLiveData.value
                                    ).await()
                                issueRepository.saveIfDoNotExist(missingIssues)
                                withContext(Dispatchers.Main) {
                                    lastDownloadedDateLiveData.value = missingIssues.last().date
                                }
                            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                                log.warn("$e")
                                break
                            }
                        }
                    }
                } finally {
                    withContext(NonCancellable) {
                        isDownloading.set(false)
                    }
                }
            }
        }
    }

    fun startMissingDownloads(dateToDownloadFrom: String) {
        log.debug("startMissingDownloads: $dateToDownloadFrom")
        CoroutineScope(Dispatchers.Main).launch {
            val minDate: String? = withContext(Dispatchers.IO) {
                return@withContext if (dateToDownloadFromLiveData.value == "" || dateToDownloadFrom < dateToDownloadFromLiveData.value) {
                    feedRepository.getAll().fold(null) { acc: String?, feed ->
                        if (acc != null && acc < feed.issueMinDate) acc else feed.issueMinDate
                    }
                } else null
            }
            synchronized(dateToDownloadFromLiveData) {
                if (!minDate.isNullOrBlank()) {
                    dateToDownloadFromLiveData.value = if (minDate < dateToDownloadFrom) {
                        dateToDownloadFrom
                    } else {
                        minDate
                    }
                }
            }
            startMissingDownloads()
        }
    }

    suspend fun cancelDownloadsAndStartAgain() = withContext(Dispatchers.Main) {
        log.debug("cancelling ToDownloadIssueHelper")
        downloadingJob?.cancelAndJoin()

        synchronized(lastDownloadedDateLiveData) {
            lastDownloadedDateLiveData.value = ""
            dateToDownloadFromLiveData.value = ""
        }
    }

}