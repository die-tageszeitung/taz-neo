package de.taz.app.android.singletons

import android.content.Context
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.util.SharedPreferenceStringLiveData
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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

    private var downloadedIssueBoolean = AtomicBoolean(false)

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
        if (!downloadedIssueBoolean.getAndSet(true)) {
            CoroutineScope(Dispatchers.IO).launch {
                while (downloadIssueNumberLiveData.value?.size ?: 0 > getStoredIssuesNumber()) {
                    issueRepository.getEarliestDownloadedIssue()?.deleteFiles()
                }
                downloadedIssueBoolean.set(false)
            }
        }
    }

    private fun getStoredIssuesNumber(): Int = storedIssueNumberLiveData.value?.toInt()
        ?: SETTINGS_GENERAL_KEEP_ISSUES_DEFAULT
}
