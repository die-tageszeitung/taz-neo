package de.taz.app.android.singletons

import android.content.Context
import android.content.SharedPreferences
import de.taz.app.android.api.ApiService
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
    private val apiService = ApiService.getInstance(applicationContext)
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
            log.debug("startingMissingDownloads")
            downloadingJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    while (
                        !dateToDownloadFromLiveData.value.isNullOrEmpty() &&
                        !lastDownloadedDateLiveData.value.isNullOrEmpty() &&
                        DateHelper.dayDelta(
                            dateToDownloadFromLiveData.value ?: "",
                            lastDownloadedDateLiveData.value ?: ""
                        ).toInt() > 0
                    ) {
                        try {
                            val missingIssues =
                                apiService.getIssuesByDateAsync(
                                    lastDownloadedDateLiveData.value ?: ""
                                ).await()
                            issueRepository.saveIfDoNotExist(missingIssues)
                            withContext(Dispatchers.Main) {
                                lastDownloadedDateLiveData.setValue(missingIssues.last().date)
                            }
                        } catch (e: ApiService.ApiServiceException.NoInternetException) {
                            log.warn("$e")
                            break
                        }
                    }
                } finally {
                    withContext(NonCancellable) {
                        isDownloading.set(false)
                        log.debug("startingMissingDownloads done")
                    }
                }
            }
        }
    }

    fun startMissingDownloads(dateToDownloadFrom: String, latestDownloadedDate: String) {
        log.debug("startMissingDownloads: $dateToDownloadFrom - $latestDownloadedDate")
        CoroutineScope(Dispatchers.Main).launch {
            val prefsDateToDownloadFrom = dateToDownloadFromLiveData.value ?: ""
            val prefsLastDownloadedDate = lastDownloadedDateLiveData.value ?: ""
            if (prefsDateToDownloadFrom == "" || dateToDownloadFrom < prefsDateToDownloadFrom) {
                // TODO GET min FEED MIN DATE of all feeds
                val tazbla = withContext(Dispatchers.IO) {
                    FeedRepository.getInstance().get("taz").issueMinDate
                }

                dateToDownloadFromLiveData.setValue(
                    if (tazbla < dateToDownloadFrom) {
                        dateToDownloadFrom
                    } else {
                        tazbla
                    }
                )
            }
            if (prefsLastDownloadedDate == "" || latestDownloadedDate > prefsLastDownloadedDate) {
                lastDownloadedDateLiveData.setValue(latestDownloadedDate)
            }
            startMissingDownloads()
        }
    }

    suspend fun cancelDownloads() = withContext(Dispatchers.Main) {
        downloadingJob?.cancelAndJoin()
        lastDownloadedDateLiveData.setValue("")
        dateToDownloadFromLiveData.setValue("")
    }
}