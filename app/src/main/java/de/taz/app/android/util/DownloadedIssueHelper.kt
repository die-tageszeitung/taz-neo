package de.taz.app.android.util

import android.content.Context
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.persistence.repository.IssueRepository

// TODO 20 to constant

class DownloadedIssueHelper private constructor(applicationContext: Context){

    companion object : SingletonHolder<DownloadedIssueHelper, Context>(::DownloadedIssueHelper)

    private val issueRepository = IssueRepository.getInstance()

    private val generalSettings = applicationContext.getSharedPreferences(
        PREFERENCES_GENERAL, Context.MODE_PRIVATE
    )

    private val storedIssueNumberLiveData = SharedPreferenceIntLiveData(
        generalSettings, "general_keep_number_issues", 20
    )
    private val downloadIssueNumberLiveData = issueRepository.getAllDownloadedStubsLiveData()

    init {
        storedIssueNumberLiveData.observeForever { ensureIssueCount() }
        downloadIssueNumberLiveData.observeForever { ensureIssueCount() }
    }

    private fun ensureIssueCount() {
        var downloadedIssueCount = downloadIssueNumberLiveData.value?.size ?: 0
        val storedIssuePreference = storedIssueNumberLiveData.value ?: 20

        while (downloadedIssueCount > storedIssuePreference) {
            issueRepository.getEarliestDownloadedIssue()?.let {
                it.deleteFiles()
                downloadedIssueCount--
            }
        }
    }
}
