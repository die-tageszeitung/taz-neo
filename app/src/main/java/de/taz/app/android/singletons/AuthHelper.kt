package de.taz.app.android.singletons

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ProcessLifecycleOwner
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.R
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.content.ContentService
import de.taz.app.android.data.DataService
import de.taz.app.android.dataStore.MappingDataStoreEntry
import de.taz.app.android.dataStore.SimpleDataStoreEntry
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.util.*
import kotlinx.coroutines.*

// region old setting names
private const val PREFERENCES_AUTH = "auth"
// endregion

// region setting keys
private const val PREFERENCES_AUTH_EMAIL = "email"
private const val PREFERENCES_AUTH_INSTALLATION_ID = "installation_id"
private const val PREFERENCES_AUTH_POLL = "poll"
private const val PREFERENCES_AUTH_STATUS = "status"
private const val PREFERENCES_AUTH_TOKEN = "token"
private const val PREFERENCES_AUTH_ELAPSED_BUT_WAITING = "elapsed_but_waiting"
// endregion


private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(
    PREFERENCES_AUTH,
    produceMigrations = {
        listOf(
            SharedPreferencesMigration(
                it,
                PREFERENCES_AUTH
            ),
        )
    }
)

/**
 * Singleton handling authentication
 */
@Mockable
class AuthHelper @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) constructor(
    applicationContext: Context,
    dataStore: DataStore<Preferences>
) {

    companion object : SingletonHolder<AuthHelper, Context>(::AuthHelper)

    private constructor(applicationContext: Context) : this(
        applicationContext,
        applicationContext.authDataStore
    )

    private val contentService by lazy { ContentService.getInstance(applicationContext) }
    private val dataService by lazy { DataService.getInstance(applicationContext) }
    private val articleRepository by lazy { ArticleRepository.getInstance(applicationContext) }
    private val toastHelper by lazy { ToastHelper.getInstance(applicationContext) }
    private val firebaseHelper by lazy { FirebaseHelper.getInstance(applicationContext) }

    val elapsedButWaiting = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(PREFERENCES_AUTH_ELAPSED_BUT_WAITING), false
    )

    val email = SimpleDataStoreEntry(
        dataStore, stringPreferencesKey(PREFERENCES_AUTH_EMAIL), ""
    )

    val isPolling = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(PREFERENCES_AUTH_POLL), false
    )

    val installationId = SimpleDataStoreEntry(
        dataStore, stringPreferencesKey(PREFERENCES_AUTH_INSTALLATION_ID), ""
    )

    val status = MappingDataStoreEntry(
        dataStore, stringPreferencesKey(PREFERENCES_AUTH_STATUS), AuthStatus.notValid,
        { authStatus -> authStatus.name }, { string -> AuthStatus.valueOf(string) }
    )

    val token = SimpleDataStoreEntry(
        dataStore, stringPreferencesKey(PREFERENCES_AUTH_TOKEN), ""
    )

    suspend fun isElapsed(): Boolean = status.get() == AuthStatus.elapsed
    suspend fun isLoggedIn(): Boolean = status.get() == AuthStatus.valid

    suspend fun getEligibleIssueStatus() =
        if (isLoggedIn()) IssueStatus.regular else IssueStatus.public

    private var deletionJob: Job? = null

    init {
        CoroutineScope(Dispatchers.Main).launch {
            deletionJob?.cancel()

            status.asLiveData().observeDistinctIgnoreFirst(ProcessLifecycleOwner.get()) { authStatus ->
                log.debug("AuthStatus changed to $authStatus")
                when (authStatus) {
                    AuthStatus.elapsed -> {
                        toastHelper.showToast(R.string.toast_logout_elapsed)
                    }
                    AuthStatus.notValid -> {
                        CoroutineScope(Dispatchers.Main).launch {
                            elapsedButWaiting.set(false)
                            toastHelper.showToast(R.string.toast_logout_invalid)
                        }
                    }
                    AuthStatus.valid -> {
                        CoroutineScope(Dispatchers.IO).launch {
                            elapsedButWaiting.set(false)
                            launch {
                                firebaseHelper.token.get()?.let {
                                    dataService.sendNotificationInfo(it, retryOnFailure = true)
                                }
                            }
                            transformBookmarks()
                            isPolling.set(false)
                        }
                    }
                    else -> Unit
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

    private suspend fun getArticleIssue(articleStub: ArticleStub): Issue {
        return contentService.downloadMetadataIfNotPresent(
            IssuePublication(
                articleStub.issueFeedName,
                articleStub.issueDate
            )
        ) as Issue
    }
}
