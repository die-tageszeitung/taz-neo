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
import de.taz.app.android.R
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.content.ContentService
import de.taz.app.android.dataStore.MappingDataStoreEntry
import de.taz.app.android.dataStore.SimpleDataStoreEntry
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.login.LoginViewModelState
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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
private const val PREFERENCES_ELAPSED_FORM_ALREADY_SENT = "elapsed_form_already_sent"
private const val PREFERENCES_AUTH_INFO_MESSAGE = "info_message"
private const val PREFERENCES_AUTH_LOGIN_WEEK = "preferences_auth_login_week"
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
class AuthHelper @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE) constructor(
    applicationContext: Context,
    dataStore: DataStore<Preferences>
): CoroutineScope {

    companion object : SingletonHolder<AuthHelper, Context>(::AuthHelper)

    private constructor(applicationContext: Context) : this(
        applicationContext,
        applicationContext.authDataStore
    )

    override val coroutineContext = SupervisorJob() + Dispatchers.Default

    // Use lazy to break an infinite getInstance loop
    private val contentService by lazy { ContentService.getInstance(applicationContext) }
    private val bookmarkRepository by lazy { BookmarkRepository.getInstance(applicationContext) }
    private val toastHelper by lazy { ToastHelper.getInstance(applicationContext) }
    private val firebaseHelper by lazy { FirebaseHelper.getInstance(applicationContext) }
    private val tracker by lazy { Tracker.getInstance(applicationContext) }

    /**
     * determines if a follow up registration was already triggered by the app via [LoginViewModelState.REGISTRATION_EMAIL]
     */
    @Deprecated("""This will never be true as we are not allowing to make a subscription
        |from within the app anymore, but maybe in future with in-app purchases""")
    val elapsedButWaiting: SimpleDataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(PREFERENCES_AUTH_ELAPSED_BUT_WAITING), false
    )

    /*
     * determines if the [SubscriptionElapsedBottomSheet] form was already sent to subscription service
     */
    val elapsedFormAlreadySent: SimpleDataStoreEntry<Boolean> = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(PREFERENCES_ELAPSED_FORM_ALREADY_SENT), false
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

    val status = MappingDataStoreEntry(
        dataStore, stringPreferencesKey(PREFERENCES_AUTH_STATUS), AuthStatus.notValid,
        { authStatus -> authStatus.name }, { string -> AuthStatus.valueOf(string) }
    )

    val token = SimpleDataStoreEntry(
        dataStore, stringPreferencesKey(PREFERENCES_AUTH_TOKEN), ""
    )

    /** True if the user is logged with a week/wochentaz abo */
    val isLoginWeek = SimpleDataStoreEntry(
        dataStore, booleanPreferencesKey(PREFERENCES_AUTH_LOGIN_WEEK), false
    )

    suspend fun isElapsed(): Boolean = status.get() == AuthStatus.elapsed
    val isElapsedFlow = status.asFlow().map { it == AuthStatus.elapsed }

    suspend fun isValid(): Boolean = status.get() == AuthStatus.valid
    suspend fun isLoggedIn(): Boolean = status.get().isLoggedIn()
    suspend fun isInternalTazUser(): Boolean = isLoggedIn() && email.get().endsWith("@taz.de")

    suspend fun getMinStatus() =
        if (isValid()) IssueStatus.regular else IssueStatus.public

    val minStatusLiveData = status.asLiveData().map {
        if (it == AuthStatus.valid) IssueStatus.regular else IssueStatus.public
    }

    init {
        launch {
            var prevAuthStatus: AuthStatus? = null
            status.asFlow().collect { authStatus ->
                log.debug("AuthStatus changed to $authStatus")
                when {
                    authStatus.isLoggedIn() && prevAuthStatus?.isAnonymous() == true -> onLogin(authStatus)
                    authStatus.isAnonymous() && prevAuthStatus?.isLoggedIn() == true -> onLogout()
                    authStatus == AuthStatus.valid && prevAuthStatus == AuthStatus.elapsed -> onSubscriptionRenewed()
                    authStatus == AuthStatus.elapsed && prevAuthStatus == AuthStatus.valid -> onSubscriptionElapsed()
                }
                prevAuthStatus = authStatus
            }
        }
    }

    private suspend fun onLogin(authStatus: AuthStatus) {
        tracker.apply {
            trackUserLoginEvent()
            startNewSession()
        }
        firebaseHelper.ensureTokenSent()
        isPolling.set(false)

        if (authStatus == AuthStatus.valid) {
            transformBookmarks()
        }
    }

    private suspend fun onLogout() {
        tracker.apply {
            trackUserLogoutEvent()
            startNewSession()
        }
        elapsedButWaiting.set(false)
        elapsedFormAlreadySent.set(false)
        isLoginWeek.set(false)
        email.set("")
        token.set("")
        toastHelper.showToast(R.string.toast_logout_invalid)
    }

    private suspend fun onSubscriptionRenewed() {
        tracker.trackUserSubscriptionRenewedEvent()
        elapsedButWaiting.set(false)
        elapsedFormAlreadySent.set(false)
        transformBookmarks()
    }

    private suspend fun onSubscriptionElapsed() {
        tracker.trackUserSubscriptionElapsedEvent()
    }

    private suspend fun transformBookmarks() {
        // Re-Download the bookmarks for the current AuthStatus - if the user logged the
        // regular issues will be downloaded.
        bookmarkRepository.getBookmarkedArticleStubs().forEach { articleStub ->
            getArticleIssue(articleStub)
        }
        bookmarkRepository.migratePublicBookmarks()
    }

    private suspend fun getArticleIssue(articleStub: ArticleStub): Issue {
        // We don't really need the returned Issue, but it should not matter too much here,
        // as login/logout are not happening that often, and it is called on a background co-routine
        return contentService.downloadMetadata(
            IssuePublication(
                articleStub.issueFeedName,
                articleStub.issueDate
            )
        ) as Issue
    }

    private fun AuthStatus.isLoggedIn(): Boolean = (this == AuthStatus.valid || this == AuthStatus.elapsed)
    private fun AuthStatus.isAnonymous(): Boolean = (this == AuthStatus.notValid)
}
