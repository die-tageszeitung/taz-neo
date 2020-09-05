package de.taz.app.android.singletons

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Issue
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

const val PREFERENCES_AUTH = "auth"
const val PREFERENCES_AUTH_EMAIL = "email"
const val PREFERENCES_AUTH_INSTALLATION_ID = "installation_id"
const val PREFERENCES_AUTH_POLL = "poll"
const val PREFERENCES_AUTH_STATUS = "status"
const val PREFERENCES_AUTH_TOKEN = "token"

/**
 * Singleton handling authentication
 */
@Mockable
class AuthHelper private constructor(val applicationContext: Context) : ViewModel() {

    companion object : SingletonHolder<AuthHelper, Context>(::AuthHelper)

    private val apiService
        get() = ApiService.getInstance(applicationContext)
    private val articleRepository
        get() = ArticleRepository.getInstance(applicationContext)
    private val issueRepository
        get() = IssueRepository.getInstance(applicationContext)
    private val toastHelper
        get() = ToastHelper.getInstance(applicationContext)
    private val sectionRepository
        get() = SectionRepository.getInstance(applicationContext)
    private val toDownloadIssueHelper
        get() = ToDownloadIssueHelper.getInstance(applicationContext)


    private val preferences =
        applicationContext.getSharedPreferences(PREFERENCES_AUTH, Context.MODE_PRIVATE)

    var tokenLiveData =
        SharedPreferenceStringLiveData(
            preferences,
            PREFERENCES_AUTH_TOKEN,
            ""
        )
    var token
        get() = tokenLiveData.value ?: ""
        set(value) = tokenLiveData.postValue(value)

    val installationId
        get() = SharedPreferenceStringLiveData(
            preferences, PREFERENCES_AUTH_INSTALLATION_ID, ""
        ).value ?: ""

    var authStatusLiveData: MutableLiveData<AuthStatus> =
        SharedPreferencesAuthStatusLiveData(
            preferences, PREFERENCES_AUTH_STATUS, AuthStatus.notValid
        )
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) set

    var authStatus: AuthStatus
        get() = authStatusLiveData.value ?: AuthStatus.notValid
        set(value) = authStatusLiveData.postValue(value)

    fun isLoggedIn(): Boolean = authStatus == AuthStatus.valid

    val emailLiveData = SharedPreferenceStringLiveData(
        preferences, PREFERENCES_AUTH_EMAIL, ""
    )

    var email
        get() = emailLiveData.value
        set(value) = emailLiveData.postValue(value)

    val isPollingLiveData = SharedPreferenceBooleanLiveData(
        preferences, PREFERENCES_AUTH_POLL, false
    )

    var isPolling: Boolean
        get() = isPollingLiveData.value ?: false
        set(value) = isPollingLiveData.postValue(value)

    private var deletionJob: Job? = null

    init {
        CoroutineScope(Dispatchers.Main).launch {
            deletionJob?.cancel()

            authStatusLiveData.observeDistinctIgnoreFirst(ProcessLifecycleOwner.get()) { authStatus ->
                log.debug("AuthStatus changed to $authStatus")
                if (authStatus == AuthStatus.elapsed) {
                    cancelAndStartDownloadingPublicIssues()
                    toastHelper.showToast(R.string.toast_logout_elapsed)
                }
                if (authStatus == AuthStatus.notValid) {
                    cancelAndStartDownloadingPublicIssues()
                    toastHelper.showToast(R.string.toast_logout_invalid)
                }
                if (authStatus == AuthStatus.valid) {
                    CoroutineScope(Dispatchers.IO).launch {
                        toDownloadIssueHelper.cancelDownloadsAndStartAgain()
                        ApiService.getInstance(applicationContext).sendNotificationInfoAsync()
                        isPolling = false
                        transformBookmarks()

                    }
                }
            }
        }
    }

    private fun cancelAndStartDownloadingPublicIssues() = CoroutineScope(Dispatchers.IO).launch {
        toDownloadIssueHelper.cancelDownloadsAndStartAgain()
        issueRepository.getEarliestIssue()?.let { earliest ->
            toDownloadIssueHelper.startMissingDownloads(earliest.date)
        }
    }

    private suspend fun transformBookmarks() {
        val bookmarkedMinDate: String =
            articleRepository.getBookmarkedArticleStubList().fold("") { acc, articleStub ->
                getArticleIssue(articleStub)?.let { issue ->
                    issueRepository.saveIfDoesNotExist(issue)
                    articleRepository.apply {
                        debookmarkArticle(articleStub)
                        bookmarkArticle(articleStub.articleFileName.replace("public.", ""))
                    }
                    if (acc == "" || issue.date < acc) {
                        issue.date
                    } else {
                        acc
                    }
                } ?: ""
            }
        if (bookmarkedMinDate.isNotBlank()) {
            toDownloadIssueHelper.startMissingDownloads(bookmarkedMinDate)
        }
    }

    private suspend fun getArticleIssue(articleStub: ArticleStub): Issue? {
        return apiService.getIssueByFeedAndDateAsync(
            articleStub.issueFeedName, articleStub.issueDate
        ).await()
    }
}
