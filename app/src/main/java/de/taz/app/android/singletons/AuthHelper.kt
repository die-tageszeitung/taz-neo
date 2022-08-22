package de.taz.app.android.singletons

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.map
import androidx.room.withTransaction
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.R
import de.taz.app.android.api.dto.CustomerType
import de.taz.app.android.api.models.*
import de.taz.app.android.content.ContentService
import de.taz.app.android.dataStore.MappingDataStoreEntry
import de.taz.app.android.dataStore.SimpleDataStoreEntry
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

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
private const val PREFERENCES_AUTH_INFO_MESSAGE = "info_message"
private const val PREFERENCES_AUTH_CUSTOMER_TYPE = "customer_type"
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

    val elapsedDateMessage = SimpleDataStoreEntry(
        dataStore, stringPreferencesKey(PREFERENCES_AUTH_INFO_MESSAGE), ""
    )

    final val status = MappingDataStoreEntry(
        dataStore, stringPreferencesKey(PREFERENCES_AUTH_STATUS), AuthStatus.notValid,
        { authStatus -> authStatus.name }, { string -> AuthStatus.valueOf(string) }
    )

    val token = SimpleDataStoreEntry(
        dataStore, stringPreferencesKey(PREFERENCES_AUTH_TOKEN), ""
    )

    // TODO remove once tokens for elapsed trialSubscription login implemented
    val customerType = MappingDataStoreEntry<CustomerType?, String>(
        dataStore,
        stringPreferencesKey(PREFERENCES_AUTH_CUSTOMER_TYPE),
        null,
        { it?.toString() ?: "" },
        { string -> if (string.isEmpty()) null else CustomerType.valueOf(string) }
    )

    suspend fun isElapsed(): Boolean = status.get() == AuthStatus.elapsed
    suspend fun isValid(): Boolean = status.get() == AuthStatus.valid
    suspend fun isLoggedIn(): Boolean = status.get() == AuthStatus.valid || status.get() == AuthStatus.elapsed

    suspend fun getMinStatus() =
        if (isValid()) IssueStatus.regular else IssueStatus.public

    val minStatusLiveData = status.asLiveData().map {
        if (it == AuthStatus.valid) IssueStatus.regular else IssueStatus.public
    }

    init {
        CoroutineScope(Dispatchers.Default).launch {
            status.asFlow().distinctUntilChanged().drop(1).collect { authStatus ->
                log.debug("AuthStatus changed to $authStatus")
                when (authStatus) {
                    AuthStatus.notValid -> {
                        elapsedButWaiting.set(false)
                        toastHelper.showToast(R.string.toast_logout_invalid)
                    }
                    AuthStatus.valid -> {
                        elapsedButWaiting.set(false)
                        firebaseHelper.ensureTokenSent()
                        transformBookmarks()
                        isPolling.set(false)
                        // clear info once valid again
                        customerType.set(null)
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
        articleRepository.appDatabase.withTransaction {
            articleRepository.getBookmarkedArticleStubs().forEach { articleStub ->
                articleStub.bookmarkedTime?.let { date ->
                    articleRepository.setBookmarkedTime(
                        articleStub.articleFileName.replace(
                            "public.",
                            ""
                        ), date
                    )
                }
                articleRepository.debookmarkArticle(articleStub)
            }
        }
    }

    private suspend fun getArticleIssue(articleStub: ArticleStub): Issue {
        return contentService.downloadMetadata(
            IssuePublication(
                articleStub.issueFeedName,
                articleStub.issueDate
            )
        ) as Issue
    }
}
