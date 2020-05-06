package de.taz.app.android.singletons

import de.taz.app.android.api.ApiService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.Log

object ToDownloadIssueHelper {

    private val log by Log
    private val issueRepository = IssueRepository.getInstance()
    private val dateHelper = DateHelper.getInstance()
    private val apiService = ApiService.getInstance()

    suspend fun startMissingDownloads(fromDate: String, toDate: String) {
        var updatedToDate = toDate
        val missingIssuesCount = dateHelper.dayDelta(fromDate, toDate).toInt()
        // we download missing issues in batches of 10, since API call has upper limit
        val necessaryNumberAPICalls = missingIssuesCount / 10 + 1
        log.debug("necessary number of API calls: $necessaryNumberAPICalls")
        log.debug("toDate at the beginning: $toDate")
        for (i in 1..necessaryNumberAPICalls ) {
            log.debug("downloading $i batch of missing issues")
            val missingIssues = apiService.getIssuesByDate(updatedToDate)
            missingIssues.forEach {
                issueRepository.save(it)
                updatedToDate = it.date
            }
            log.debug("reset earliestDate to $toDate")
        }
    }

}