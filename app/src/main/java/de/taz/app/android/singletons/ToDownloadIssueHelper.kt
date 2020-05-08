package de.taz.app.android.singletons

import android.content.Context
import android.content.SharedPreferences
import de.taz.app.android.api.ApiService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val SHARED_PREFERENCES_GAP_TO_DOWNLOAD = "shared_preferences_gap_to_download"
const val LAST_DOWNLOADED_DATE = "shared_preferences_last_downloaded_date"
const val DATE_TO_DOWNLOAD_FROM = "shared_preferences_date_to_download_from"

/**
 * Singleton to keep track of issueStubs that needs to be downloaded.
 */
class ToDownloadIssueHelper(applicationContext: Context) {

    companion object : SingletonHolder<ToDownloadIssueHelper, Context>(::ToDownloadIssueHelper)

    private val log by Log
    private val issueRepository = IssueRepository.getInstance()
    private val dateHelper = DateHelper.getInstance()
    private val apiService = ApiService.getInstance()
    var prefs: SharedPreferences = applicationContext.getSharedPreferences(
        SHARED_PREFERENCES_GAP_TO_DOWNLOAD, Context.MODE_PRIVATE
    )
    var editPrefs: SharedPreferences.Editor = prefs.edit()

    init {
        val lastDownloadedDate = prefs.getString(LAST_DOWNLOADED_DATE, "")
        val dateToDownloadFrom = prefs.getString(DATE_TO_DOWNLOAD_FROM, "")

        if (dateToDownloadFrom != null && lastDownloadedDate != null && dateToDownloadFrom < lastDownloadedDate) {
            CoroutineScope(Dispatchers.IO).launch {
                startMissingDownloads(dateToDownloadFrom, lastDownloadedDate)
            }
        }
    }

    suspend fun startMissingDownloads(dateToDownloadFrom: String, latestDownloadedDate: String) {
        val prefsDateToDownloadFrom = prefs.getString(DATE_TO_DOWNLOAD_FROM,"")
        val prefsLastDownloadedDate = prefs.getString(LAST_DOWNLOADED_DATE,"")
        if (prefsDateToDownloadFrom == null || dateToDownloadFrom < prefsDateToDownloadFrom) {
            editPrefs.putString(DATE_TO_DOWNLOAD_FROM, dateToDownloadFrom)
                .apply()
        }
        if (prefsLastDownloadedDate == null || latestDownloadedDate < prefsLastDownloadedDate) {
            editPrefs.putString(LAST_DOWNLOADED_DATE, latestDownloadedDate)
                .apply()
        }
        var newLatestDownloadedDate = latestDownloadedDate
        val missingIssuesCount = dateHelper.dayDelta(dateToDownloadFrom, latestDownloadedDate).toInt()
        // we download missing issues in batches of 10, since API call has upper limit
        val necessaryNumberAPICalls = missingIssuesCount / 10 + 1
        log.debug("necessary number of API calls: $necessaryNumberAPICalls")
        log.debug("loading issue between $dateToDownloadFrom and $latestDownloadedDate")
        for (i in 1..necessaryNumberAPICalls) {
            log.debug("downloading $i. batch of missing issues")
            try {
                val missingIssues = apiService.getIssuesByDate(newLatestDownloadedDate)
                missingIssues.forEach {
                    issueRepository.save(it)
                    newLatestDownloadedDate = it.date

                    editPrefs
                        .putString(LAST_DOWNLOADED_DATE, newLatestDownloadedDate)
                        .apply()
                }
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                log.warn("$e")
                break
            }
            log.debug("reset latestDownloadedDate to $newLatestDownloadedDate")
        }
    }
}