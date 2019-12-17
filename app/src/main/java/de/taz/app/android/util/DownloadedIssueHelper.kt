package de.taz.app.android.util

import android.content.Context
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val KEEP_ISSUES_DOWNLOADED_DEFAULT = 20

class DownloadedIssueHelper private constructor(applicationContext: Context){

    companion object : SingletonHolder<DownloadedIssueHelper, Context>(::DownloadedIssueHelper)

    private val issueRepository = IssueRepository.getInstance()

    private val generalSettings = applicationContext.getSharedPreferences(
        PREFERENCES_GENERAL, Context.MODE_PRIVATE
    )

    private val storedIssueNumberLiveData = SharedPreferenceStringLiveData(
        generalSettings, "general_keep_number_issues", KEEP_ISSUES_DOWNLOADED_DEFAULT.toString()
    )
    private val downloadIssueNumberLiveData = issueRepository.getAllDownloadedStubsLiveData()

    init {
        storedIssueNumberLiveData.observeForever { ensureIssueCount() }
        downloadIssueNumberLiveData.observeForever { ensureIssueCount() }
    }

    private fun ensureIssueCount() {
        CoroutineScope(Dispatchers.IO).launch {
            var downloadedIssueCount = downloadIssueNumberLiveData.value?.size ?: 0
            val storedIssuePreference = storedIssueNumberLiveData.value?.toInt() ?: KEEP_ISSUES_DOWNLOADED_DEFAULT

            while (downloadedIssueCount > storedIssuePreference) {
                issueRepository.getEarliestDownloadedIssue()?.let {
                    it.deleteFiles()
                    downloadedIssueCount--
                }
            }
        }
    }
}
