package de.taz.app.android.singletons

import android.content.Context
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.util.SharedPreferenceStringLiveData
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val SETTINGS_GENERAL_KEEP_ISSUES = "general_keep_number_issues"
const val SETTINGS_GENERAL_KEEP_ISSUES_DEFAULT = 20
const val SETTINGS_DOWNLOAD_ONLY_WIFI = "download_only_wifi"
const val SETTINGS_DOWNLOAD_ENABLED = "download_enabled"

class DownloadedIssueHelper private constructor(applicationContext: Context) {

    companion object : SingletonHolder<DownloadedIssueHelper, Context>(::DownloadedIssueHelper)

    private val issueRepository = IssueRepository.getInstance(applicationContext)

    private val generalSettings = applicationContext.getSharedPreferences(
        PREFERENCES_GENERAL, Context.MODE_PRIVATE
    )

    private val storedIssueNumberLiveData =
        SharedPreferenceStringLiveData(
            generalSettings,
            SETTINGS_GENERAL_KEEP_ISSUES,
            SETTINGS_GENERAL_KEEP_ISSUES_DEFAULT.toString()
        )
    private val downloadIssueNumberLiveData = issueRepository.getAllDownloadedStubsLiveData()

    init {
        storedIssueNumberLiveData.observeForever { ensureIssueCount() }
        downloadIssueNumberLiveData.observeForever { ensureIssueCount() }
    }

    private fun ensureIssueCount() {
        CoroutineScope(Dispatchers.IO).launch {
            var downloadedIssueCount = downloadIssueNumberLiveData.value?.size ?: 0
            val storedIssuePreference = storedIssueNumberLiveData.value?.toInt()
                ?: SETTINGS_GENERAL_KEEP_ISSUES_DEFAULT

            while (downloadedIssueCount > storedIssuePreference) {
                issueRepository.getEarliestDownloadedIssue()?.let {
                    it.deleteFiles()
                    downloadedIssueCount--
                }
            }
        }
    }
}
