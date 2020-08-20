package de.taz.app.android.singletons

import android.content.Context
import android.content.SharedPreferences
import de.taz.app.android.api.ApiService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.Log
import de.taz.app.android.util.SharedPreferenceStringLiveData
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    var prefs: SharedPreferences = applicationContext.getSharedPreferences(
        SHARED_PREFERENCES_GAP_TO_DOWNLOAD, Context.MODE_PRIVATE
    )
    private var editPrefs: SharedPreferences.Editor = prefs.edit()

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
                            apiService.getIssuesByDateAsync(lastDownloadedDateLiveData.value ?: "")
                                .await()
                        missingIssues.forEach {
                            issueRepository.saveIfDoesNotExist(it)
                        }
                        editPrefs
                            .putString(LAST_DOWNLOADED_DATE, missingIssues.last().date)
                            .apply()
                    } catch (e: ApiService.ApiServiceException.NoInternetException) {
                        log.warn("$e")
                        break
                    }
                }
            }.also {
                it.invokeOnCompletion {
                    isDownloading.set(false)
                }
            }
        }
    }

    fun startMissingDownloads(dateToDownloadFrom: String, latestDownloadedDate: String) {
        downloadingJob?.cancel()
        val prefsDateToDownloadFrom = dateToDownloadFromLiveData.value ?: ""
        val prefsLastDownloadedDate = lastDownloadedDateLiveData.value ?: ""
        if (prefsDateToDownloadFrom == "" || dateToDownloadFrom < prefsDateToDownloadFrom) {
            dateToDownloadFromLiveData.postValue(dateToDownloadFrom)
        }
        if (prefsLastDownloadedDate == "" || latestDownloadedDate > prefsLastDownloadedDate) {
            lastDownloadedDateLiveData.postValue(latestDownloadedDate)
        }
        startMissingDownloads()
    }
}