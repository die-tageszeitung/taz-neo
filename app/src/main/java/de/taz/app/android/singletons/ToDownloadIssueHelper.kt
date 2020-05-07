package de.taz.app.android.singletons

import android.content.Context
import android.content.SharedPreferences
import de.taz.app.android.api.ApiService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder

const val SHARED_PREFERENCES_GAP_TO_DOWNLOAD = "shared_preferences_gap_to_download"
const val EARLIEST_DATE_TO_DOWNLOAD = "shared_preferences_earliest_date_to_download"
const val LATEST_DATE_TO_DOWNLOAD = "shared_preferences_latest_date_to_download"

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

    suspend fun startMissingDownloads(fromDate: String, toDate: String) {
        editPrefs
            .putString(LATEST_DATE_TO_DOWNLOAD, fromDate)
            .putString(EARLIEST_DATE_TO_DOWNLOAD, toDate)
            .apply()
        var updatedToDate = toDate
        val missingIssuesCount = dateHelper.dayDelta(fromDate, toDate).toInt()
        // we download missing issues in batches of 10, since API call has upper limit
        val necessaryNumberAPICalls = missingIssuesCount / 10 + 1
        log.debug("necessary number of API calls: $necessaryNumberAPICalls")
        log.debug("loading issue between $fromDate and $toDate")
        for (i in 1..necessaryNumberAPICalls) {
            log.debug("downloading $i. batch of missing issues")
            try {
                val missingIssues = apiService.getIssuesByDate(updatedToDate)
                missingIssues.forEach {
                    issueRepository.save(it)
                    updatedToDate = it.date

                    editPrefs
                        .putString(EARLIEST_DATE_TO_DOWNLOAD, updatedToDate)
                        .apply()
                }
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                log.warn("$e")
                break
            }
            log.debug("reset earliestDate to $updatedToDate")
        }
    }
}