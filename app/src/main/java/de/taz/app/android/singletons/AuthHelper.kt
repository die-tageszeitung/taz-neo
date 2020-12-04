package de.taz.app.android.singletons

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.R
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.data.DataService
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.monkey.observeUntil
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.*
import kotlinx.coroutines.*

const val PREFERENCES_AUTH = "auth"
const val PREFERENCES_AUTH_EMAIL = "email"
const val PREFERENCES_AUTH_INSTALLATION_ID = "installation_id"
const val PREFERENCES_AUTH_POLL = "poll"
const val PREFERENCES_AUTH_STATUS = "status"
const val PREFERENCES_AUTH_TOKEN = "token"
const val PREFERENCES_AUTH_ELAPSED_BUT_WAITING = "elapsed_but_waiting"

/**
 * Singleton handling authentication
 */
@Mockable
class AuthHelper private constructor(val applicationContext: Context) : ViewModel() {

    companion object : SingletonHolder<AuthHelper, Context>(::AuthHelper)

    private val dataService
        get() = DataService.getInstance(applicationContext)
    private val articleRepository
        get() = ArticleRepository.getInstance(applicationContext)
    private val issueRepository
        get() = IssueRepository.getInstance(applicationContext)
    private val toastHelper
        get() = ToastHelper.getInstance(applicationContext)
    private val firebaseHelper
        get() = FirebaseHelper.getInstance(applicationContext)


    private val preferences =
        applicationContext.getSharedPreferences(PREFERENCES_AUTH, Context.MODE_PRIVATE)

    var token
        get() = preferences.getString(PREFERENCES_AUTH_TOKEN, null)
        set(value) = with(preferences.edit()) {
            putString(PREFERENCES_AUTH_TOKEN, value)
            commit()
        }

    val installationId
        get() = SharedPreferenceStringLiveData(
            preferences, PREFERENCES_AUTH_INSTALLATION_ID, ""
        ).value

    var authStatusLiveData: MutableLiveData<AuthStatus> =
        SharedPreferencesAuthStatusLiveData(
            preferences, PREFERENCES_AUTH_STATUS, AuthStatus.notValid
        )
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) set

    var authStatus: AuthStatus
        get() = authStatusLiveData.value ?: AuthStatus.notValid
        set(value) = authStatusLiveData.postValue(value)

    fun isElapsed(): Boolean = authStatus == AuthStatus.elapsed
    fun isLoggedIn(): Boolean = authStatus == AuthStatus.valid

    private val elapsedButWaitingLiveData = SharedPreferenceBooleanLiveData(
        preferences, PREFERENCES_AUTH_ELAPSED_BUT_WAITING, false
    )
    var elapsedButWaiting
        get() = elapsedButWaitingLiveData.value
        set(value) = elapsedButWaitingLiveData.postValue(value)

    val emailLiveData = SharedPreferenceStringLiveData(
        preferences, PREFERENCES_AUTH_EMAIL, ""
    )

    val eligibleIssueStatus get() = if (isLoggedIn()) IssueStatus.regular else IssueStatus.public

    var email
        get() = emailLiveData.value
        set(value) = emailLiveData.postValue(value)

    val isPollingLiveData = SharedPreferenceBooleanLiveData(
        preferences, PREFERENCES_AUTH_POLL, false
    )

    var isPolling: Boolean
        get() = isPollingLiveData.value
        set(value) = isPollingLiveData.postValue(value)

    private var deletionJob: Job? = null

    init {
        CoroutineScope(Dispatchers.Main).launch {
            deletionJob?.cancel()

            authStatusLiveData.observeDistinctIgnoreFirst(ProcessLifecycleOwner.get()) { authStatus ->
                log.debug("AuthStatus changed to $authStatus")
                when (authStatus) {
                    AuthStatus.elapsed -> {
                        toastHelper.showToast(R.string.toast_logout_elapsed)

                    }
                    AuthStatus.notValid -> {
                        elapsedButWaiting = false
                        toastHelper.showToast(R.string.toast_logout_invalid)

                    }
                    AuthStatus.valid -> {
                        elapsedButWaiting = false
                        CoroutineScope(Dispatchers.IO).launch {
                            launch {
                                firebaseHelper.firebaseToken?.let {
                                    dataService.sendNotificationInfo(it, retryOnFailure = true)
                                }
                            }
                            transformBookmarks()
                            isPolling = false
                        }
                    }
                    else -> {
                    }
                }
            }
        }
    }

    private suspend fun transformBookmarks() {
        articleRepository.getBookmarkedArticleStubs().forEach { articleStub ->
            getArticleIssue(articleStub)
        }
        articleRepository.appDatabase.runInTransaction<Unit> {
            articleRepository.getBookmarkedArticleStubs().forEach { articleStub ->
                articleRepository.debookmarkArticle(articleStub)
                articleRepository.bookmarkArticle(
                    articleStub.articleFileName.replace(
                        "public.",
                        ""
                    )
                )
            }
        }
    }

    private suspend fun getArticleIssue(articleStub: ArticleStub): Issue? {
        return dataService.getIssue(
            IssueKey(
                articleStub.issueFeedName,
                articleStub.issueDate,
                IssueStatus.regular
            )
        )
    }
}
