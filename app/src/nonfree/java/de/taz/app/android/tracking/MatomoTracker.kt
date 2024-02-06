package de.taz.app.android.tracking

import android.content.Context
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Section
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.matomo.sdk.Matomo
import org.matomo.sdk.QueryParams
import org.matomo.sdk.TrackMe
import org.matomo.sdk.TrackerBuilder
import org.matomo.sdk.extra.TrackHelper
import kotlin.coroutines.CoroutineContext

// region: Event Categories
private const val CATEGORY_APPLICATION = "Application"
private const val CATEGORY_USER_AUTH = "User"
private const val CATEGORY_AUTH_STATUS = "Authentication Status"
private const val CATEGORY_SUBSCRIPTION_STATUS = "Subscription Status"
private const val CATEGORY_DIALOG = "Dialog"
private const val CATEGORY_SUBSCRIPTION = "Subscription"
private const val CATEGORY_APPMODE = "AppMode"
private const val CATEGORY_BOOKMARKS = "Bookmarks"
private const val CATEGORY_SHARE = "Share"
private const val CATEGORY_DRAWER = "Drawer"
private const val CATEGORY_AUDIO_PLAYER = "Audio Player"
private const val CATEGORY_COACH_MARK = "Coachmark"
private const val CATEGORY_TAP_TO_SCROLL = "Tap am Rand"
private const val CATEGORY_ISSUE = "Issue"
private const val CATEGORY_ARTICLE = "Article"
// endregion

// region: Goals
// IDs are defined on the backend instance
private const val GOAL_INTERNAL_TAZ_USER_ID = 3
private const val GOAL_TEST_TRACKING_ID = 2
// endregion

private const val SESSION_TIMEOUT_MS = 2 * 60 * 60 * 1_000 // 2h

class MatomoTracker(applicationContext: Context) : Tracker {

    private val log by Log

    private val authHelper = AuthHelper.getInstance(applicationContext)
    private val generalDataStore = GeneralDataStore.getInstance(applicationContext)

    private val matomo = Matomo.getInstance(applicationContext)
    private val config = TrackerBuilder
        .createDefault("https://gazpacho.taz.de/matomo.php", 113)
        .setApplicationBaseUrl("https://${applicationContext.packageName}/")
    private val matomoTracker = SessionAwareTracker(matomo, config, ::onNewSession)

    init {
        matomoTracker.apply {
            // Ensure, that the tracker is initially disabled and must be enabled explicitly.
            isOptOut = true
            // Force longer sessions until the library is fixed to handle session timeouts in regard to the last sent event
            setSessionTimeout(SESSION_TIMEOUT_MS)
        }
    }

    private suspend fun onNewSession() {
        setTrackingGoals()
        when (authHelper.status.get()) {
            AuthStatus.valid -> trackUserAuthenticatedState()
            AuthStatus.elapsed -> {
                trackUserAuthenticatedState()
                trackUserSubscriptionElapsedEvent()
            }

            AuthStatus.notValid -> trackUserAnonymousState()

            // These status won't occur as a result of an authentication - only during the login process itself.
            // They won't ever be set to AuthHelper.status and can be ignored
            AuthStatus.tazIdNotLinked, AuthStatus.alreadyLinked, AuthStatus.notValidMail -> Unit
        }
    }

    private suspend fun setTrackingGoals() {
        if (authHelper.isInternalTazUser() && !generalDataStore.hasInternalTazUserGoalBeenTracked.get() && !matomoTracker.isOptOut) {
            trackInternalTazUserGoal()
            generalDataStore.hasInternalTazUserGoalBeenTracked.set(true)
        }
        if (generalDataStore.testTrackingGoalEnabled.get()) {
            trackTestTrackingGoal()
        }
    }

    private fun trackInternalTazUserGoal() {
        TrackHelper.track()
            .goal(GOAL_INTERNAL_TAZ_USER_ID)
            .revenue(0.1f)
            .with(matomoTracker)
    }

    override fun trackTestTrackingGoal() {
        TrackHelper.track()
            .goal(GOAL_TEST_TRACKING_ID)
            .revenue(0.01f)
            .with(matomoTracker)
    }

    override fun enable() {
        log.verbose("Tracking enabled")
        matomoTracker.isOptOut = false
    }

    override fun disable() {
        log.verbose("Tracking disabled")
        matomoTracker.isOptOut = true
    }

    override fun startNewSession() {
        matomoTracker.startNewSession()
    }

    override fun dispatch() {
        matomoTracker.dispatch()
    }

    override fun trackDownload(version: String) {
        TrackHelper.track().download().version(version).with(matomoTracker)
    }

    override fun trackAppIsBackgroundedEvent() {
        TrackHelper.track()
            .event(CATEGORY_APPLICATION, "Minimize")
            .with(matomoTracker)
    }

    override fun trackUserAuthenticatedState() {
        TrackHelper.track()
            .event(CATEGORY_AUTH_STATUS, "State Authenticated")
            .with(matomoTracker)
    }

    override fun trackUserAnonymousState() {
        TrackHelper.track()
            .event(CATEGORY_AUTH_STATUS, "State Anonymous")
            .with(matomoTracker)
    }

    override fun trackUserLoginEvent() {
        TrackHelper.track()
            .event(CATEGORY_USER_AUTH, "Login")
            .with(matomoTracker)
    }

    override fun trackUserLogoutEvent() {
        TrackHelper.track()
            .event(CATEGORY_USER_AUTH, "Logout")
            .with(matomoTracker)
    }

    override fun trackUserSubscriptionElapsedEvent() {
        TrackHelper.track()
            .event(CATEGORY_SUBSCRIPTION_STATUS, "Elapsed")
            .with(matomoTracker)
    }

    override fun trackUserSubscriptionRenewedEvent() {
        TrackHelper.track()
            .event(CATEGORY_SUBSCRIPTION_STATUS, "Subscription Renewed")
            .with(matomoTracker)
    }

    override fun trackCoverflowScreen(pdfMode: Boolean) {
        val screenName = if (pdfMode) "/home/coverflow/pdf" else "/home/coverflow/mobile"
        val screenTitle = if (pdfMode) "Coverflow PDF" else "Coverflow Mobile"
        TrackHelper.track().screen(screenName).title(screenTitle).with(matomoTracker)
    }

    override fun trackArchiveScreen(pdfMode: Boolean) {
        val screenName = if (pdfMode) "/home/archive/pdf" else "/home/archive/mobile"
        val screenTitle = if (pdfMode) "Archive PDF" else "Archive Mobile"
        TrackHelper.track().screen(screenName).title(screenTitle).with(matomoTracker)
    }

    override fun trackBookmarkListScreen() {
        TrackHelper.track().screen("/bookmarks/list").title("Bookmarks List").with(matomoTracker)
    }

    override fun trackSearchScreen() {
        TrackHelper.track().screen("/search").title("Search").with(matomoTracker)
    }

    override fun trackSettingsScreen() {
        TrackHelper.track().screen("/settings").title("Settings").with(matomoTracker)
    }

    private fun issuePath(issueKey: AbstractIssuePublication): String {
        return "issue/${issueKey.feedName}/${issueKey.date}"
    }

    private fun sectionPath(section: SectionOperations): String {
        return "section/${section.key}"
    }

    private fun articlePath(article: Article): String =
        articlePath(article.key, article.mediaSyncId)

    private fun articlePath(articleFileName: String, mediaSyncId: Int?): String {
        val mediaSyncSuffix = mediaSyncId ?: ""
        return "article/$articleFileName?id=$mediaSyncSuffix"
    }

    override fun trackSectionScreen(issueKey: AbstractIssuePublication, section: Section) {
        val path = "/${issuePath(issueKey)}/${sectionPath(section)}"
        TrackHelper.track()
            .screen(path)
            .title(section.getHeaderTitle())
            .with(matomoTracker)
    }

    override fun trackPdfPageScreen(issueKey: AbstractIssuePublication, pagina: String) {
        val path = "/${issuePath(issueKey)}/pdf/$pagina"
        TrackHelper.track()
            .screen(path)
            .title("PDF Page: $pagina")
            .with(matomoTracker)
    }

    override fun trackArticleScreen(
        issueKey: AbstractIssuePublication,
        sectionOperations: SectionOperations,
        article: Article
    ) {
        val path =
            "/${issuePath(issueKey)}/${sectionPath(sectionOperations)}/${articlePath(article)}"
        TrackHelper.track()
            .screen(path)
            .title(article.title)
            .with(matomoTracker)
    }

    override fun trackWebViewScreen(htmlFile: String) {
        TrackHelper.track()
            .screen("/webview/$htmlFile")
            .with(matomoTracker)
    }

    override fun trackErrorReportScreen() {
        TrackHelper.track()
            .screen("/error_report")
            .title("Error Report")
            .with(matomoTracker)
    }

    override fun trackLoginScreen() {
        TrackHelper.track()
            .screen("/login")
            .title("Login")
            .with(matomoTracker)
    }

    override fun trackForgotPasswordScreen() {
        TrackHelper.track()
            .screen("/forgot_password")
            .title("Forgot Password")
            .with(matomoTracker)
    }

    override fun trackSubscriptionSwitchFormScreen() {
        TrackHelper.track()
            .screen("/subscription/switch")
            .title("Subscription Switch To Digiabo")
            .with(matomoTracker)
    }

    override fun trackSubscriptionExtendFormScreen() {
        TrackHelper.track()
            .screen("/subscription/extend")
            .title("Subscription Extend With Digiabo")
            .with(matomoTracker)
    }

    override fun trackSubscriptionTrialInfoScreen() {
        TrackHelper.track()
            .screen("/subscription/trial_info")
            .title("Subscription Trial Info")
            .with(matomoTracker)
    }

    override fun trackSubscriptionTrialElapsedInfoScreen() {
        TrackHelper.track()
            .screen("/subscription/trial_elapsed")
            .title("Subscription Trial Elapsed Info")
            .with(matomoTracker)
    }

    override fun trackSubscriptionPersonalDataFormScreen() {
        TrackHelper.track()
            .screen("/subscription/personal_data_form")
            .title("Subscription Personal Data Form")
            .with(matomoTracker)
    }

    override fun trackSubscriptionAccountLoginFormScreen() {
        TrackHelper.track()
            .screen("/subscription/account_login")
            .title("Subscription Account Login/Create")
            .with(matomoTracker)
    }

    override fun trackLoginHelpDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "Login Help")
            .with(matomoTracker)
    }

    override fun trackSubscriptionHelpDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "Subscription Help")
            .with(matomoTracker)
    }

    override fun trackSubscriptionElapsedDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "Subscription Elapsed")
            .with(matomoTracker)
    }

    override fun trackAllowNotificationsDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "Allow Notifications Info")
            .with(matomoTracker)
    }

    override fun trackIssueActionsDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "Issue Actions")
            .with(matomoTracker)
    }

    override fun trackIssueDatePickerDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "Issue Date Picker")
            .with(matomoTracker)
    }

    override fun trackTextSettingsDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "Text Settings")
            .with(matomoTracker)
    }

    override fun trackSharingNotPossibleDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "Sharing Not Possible")
            .with(matomoTracker)
    }

    override fun trackAutomaticDownloadDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "Automatic Download Choice")
            .with(matomoTracker)
    }

    override fun trackPdfModeLoginHintDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "PDF Mode Login Hint")
            .with(matomoTracker)
    }

    override fun trackConnectionErrorDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "Connection Error")
            .with(matomoTracker)
    }

    override fun trackFatalErrorDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "Fatal Error")
            .with(matomoTracker)
    }

    override fun trackIssueDownloadErrorDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "Issue Download Error")
            .with(matomoTracker)
    }

    override fun trackSubscriptionInquirySubmittedEvent() {
        TrackHelper.track()
            .event(CATEGORY_SUBSCRIPTION, "Inquiry Submitted")
            .with(matomoTracker)
    }

    override fun trackSubscriptionInquiryFormValidationErrorEvent() {
        TrackHelper.track()
            .event(CATEGORY_SUBSCRIPTION, "Inquiry Form Validation Error")
            .with(matomoTracker)
    }

    override fun trackSubscriptionInquiryServerErrorEvent() {
        TrackHelper.track()
            .event(CATEGORY_SUBSCRIPTION, "Inquiry Server Error")
            .with(matomoTracker)
    }

    override fun trackSubscriptionInquiryNetworkErrorEvent() {
        TrackHelper.track()
            .event(CATEGORY_SUBSCRIPTION, "Inquiry Network Error")
            .with(matomoTracker)
    }

    override fun trackSubscriptionTrialConfirmedEvent() {
        TrackHelper.track()
            .event(CATEGORY_SUBSCRIPTION, "Trial Confirmed")
            .with(matomoTracker)
    }

    override fun trackSwitchToPdfModeEvent() {
        TrackHelper.track()
            .event(CATEGORY_APPMODE, "Switch to PDF Mode")
            .with(matomoTracker)
    }

    override fun trackSwitchToMobileModeEvent() {
        TrackHelper.track()
            .event(CATEGORY_APPMODE, "Switch to Mobile Mode")
            .with(matomoTracker)
    }

    override fun trackAddBookmarkEvent(articleFileName: String, mediaSyncId: Int?) {
        TrackHelper.track()
            .event(CATEGORY_BOOKMARKS, "Add Article")
            .name(articlePath(articleFileName, mediaSyncId))
            .with(matomoTracker)
    }

    override fun trackRemoveBookmarkEvent(articleFileName: String, mediaSyncId: Int?) {
        TrackHelper.track()
            .event(CATEGORY_BOOKMARKS, "Remove Article")
            .name(articlePath(articleFileName, mediaSyncId))
            .with(matomoTracker)
    }

    override fun trackShareArticleEvent(articleStub: ArticleStub) {
        trackShareArticleEvent(articleStub.articleFileName, articleStub.mediaSyncId)
    }

    override fun trackShareArticleEvent(article: Article) {
        trackShareArticleEvent(article.key, article.mediaSyncId)
    }

    private fun trackShareArticleEvent(articleFileName: String, mediaSyncId: Int?) {
        TrackHelper.track()
            .event(CATEGORY_SHARE, "Share Article")
            .name(articlePath(articleFileName, mediaSyncId))
            .with(matomoTracker)
    }

    override fun trackShareSearchHitEvent(onlineLink: String) {
        TrackHelper.track()
            .event(CATEGORY_SHARE, "Share Search Hit")
            .name(onlineLink)
            .with(matomoTracker)
    }

    override fun trackShareMomentEvent(issueKey: AbstractIssuePublication) {
        TrackHelper.track()
            .event(CATEGORY_SHARE, "Issue Moment")
            .name(issuePath(issueKey))
            .with(matomoTracker)
    }

    override fun trackDrawerOpenEvent(dragged: Boolean) {
        val eventName = if (dragged) "Dragging" else "Logo Tap"
        TrackHelper.track()
            .event(CATEGORY_DRAWER, "Open")
            .name(eventName)
            .with(matomoTracker)
    }

    override fun trackDrawerTapPageEvent() {
        TrackHelper.track()
            .event(CATEGORY_DRAWER, "Tap")
            .name("Tap Page")
            .with(matomoTracker)
    }

    override fun trackDrawerTapSectionEvent() {
        TrackHelper.track()
            .event(CATEGORY_DRAWER, "Tap")
            .name("Tap Section")
            .with(matomoTracker)
    }

    override fun trackDrawerTapArticleEvent() {
        TrackHelper.track()
            .event(CATEGORY_DRAWER, "Tap")
            .name("Tap Article")
            .with(matomoTracker)
    }

    override fun trackDrawerTapImprintEvent() {
        TrackHelper.track()
            .event(CATEGORY_DRAWER, "Tap")
            .name("Tap Imprint")
            .with(matomoTracker)
    }

    override fun trackDrawerTapMomentEvent() {
        TrackHelper.track()
            .event(CATEGORY_DRAWER, "Tap")
            .name("Tap Moment")
            .with(matomoTracker)
    }

    override fun trackDrawerTapBookmarkEvent() {
        TrackHelper.track()
            .event(CATEGORY_DRAWER, "Tap")
            .name("Tap Bookmark")
            .with(matomoTracker)
    }

    override fun trackDrawerTapPlayIssueEvent() {
        TrackHelper.track()
            .event(CATEGORY_DRAWER, "Tap")
            .name("Tap Play Issue")
            .with(matomoTracker)
    }

    override fun trackDrawerToggleAllSectionsEvent() {
        TrackHelper.track()
            .event(CATEGORY_DRAWER, "Toggle")
            .name("Toggle all Sections")
            .with(matomoTracker)
    }

    override fun trackDrawerToggleSectionEvent() {
        TrackHelper.track()
            .event(CATEGORY_DRAWER, "Toggle")
            .name("Toggle section")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerPlayArticleEvent(article: Article) {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Play Article")
            .name(articlePath(article.key, article.mediaSyncId))
            .with(matomoTracker)
    }

    override fun trackAudioPlayerPlayPodcastEvent(
        issueKey: AbstractIssuePublication,
        title: String
    ) {
        val path = "/${issuePath(issueKey)}"
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Play Podcast")
            .name("$path?title=$title")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerChangePlaySpeedEvent(playbackSpeed: Float) {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Change Play Speed")
            .name(playbackSpeed.toString())
            .with(matomoTracker)
    }

    override fun trackAudioPlayerMaximizeEvent() {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Maximize")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerMinimizeEvent() {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Minimize")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerCloseEvent() {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Close")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerSkipNextEvent() {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Skip Next")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerSkipPreviousEvent() {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Skip Previous")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerSeekForwardSecondsEvent(seconds: Long) {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Seek Forward")
            .name("$seconds Seconds")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerSeekForwardBreakEvent() {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Seek Forward")
            .name("Break")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerSeekBackwardSecondsEvent(seconds: Long) {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Seek Backward")
            .name("$seconds Seconds")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerSeekBackwardBreakEvent() {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Seek Backward")
            .name("Break")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerSeekPositionEvent() {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Seek Position")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerResumeEvent() {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Resume")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerPauseEvent() {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Pause")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerAutoplayEnableEvent() {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Enable Auto Play Next")
            .with(matomoTracker)
    }

    override fun trackAudioPlayerAutoplayDisableEvent() {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Disable Auto Play Next")
            .with(matomoTracker)
    }

    override fun trackCoachMarkShow(layoutResName: String) {
        TrackHelper.track()
            .event(CATEGORY_COACH_MARK, "Show")
            .name(layoutResName)
            .with(matomoTracker)
    }

    override fun trackCoachMarkClose(layoutResName: String) {
        TrackHelper.track()
            .event(CATEGORY_COACH_MARK, "Close")
            .name(layoutResName)
            .with(matomoTracker)
    }

    override fun trackTapToScrollSettingStatusEvent(enable: Boolean) {
        val eventName = if (enable) {
            "Ein"
        } else {
            "Aus"
        }
        TrackHelper.track()
            .event(CATEGORY_TAP_TO_SCROLL, "Status")
            .name(eventName)
            .with(matomoTracker)
    }

    override fun trackIssueDownloadEvent(issueKey: AbstractIssuePublication) {
        TrackHelper.track()
            .event(CATEGORY_ISSUE, "Download")
            .name(issueKey.date)
            .with(matomoTracker)
    }

    override fun trackArticleColumnModeEnableEvent() {
        TrackHelper.track()
            .event(CATEGORY_ARTICLE, "Enable Column Mode")
            .with(matomoTracker)
    }

    override fun trackArticleColumnModeDisableEvent() {
        TrackHelper.track()
            .event(CATEGORY_ARTICLE, "Disable Column Mode")
            .with(matomoTracker)
    }
}

/**
 * Wrapper around [org.matomo.sdk.Tracker] which is aware of special handling for new sessions.
 *
 * - It is calling [onNewSession] after the first event of a new session was sent.
 * - It delays all events of a new session to ensure they have a different event datetime string,
 *   then the event before to prevent a bug in the visit/session handling of the matomo server.
 */
private class SessionAwareTracker(
    matomo: Matomo,
    config: TrackerBuilder,
    private val onNewSession: suspend () -> Unit
) : org.matomo.sdk.Tracker(matomo, config), CoroutineScope {

    // Use a single thread (the main thread) for dispatching tracking events. By using .immediate the
    // coroutine is executed immediately if we are already on the main thread.
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main.immediate

    companion object {
        private const val MATOMO_TRUE_VALUE: String = "1"
    }

    init {
        requireNotNull(config.applicationBaseUrl) { "TrackerBuilder must contain a valid applicationBaseUrl" }
    }

    // Mutex used to guard changes to [newSessionRequested] and [lastEventTimeMs] properties.
    // Locking a mutex is fair: the first to await it will get a lock on it first, thus we get some
    // kind of queue with it.
    private val mutex = Mutex()

    // True when a new session was requested via [startNewSession()]. We can't handle the case of
    // new sessions due to the internal timeouts within [org.matomo.sdk.Tracker]
    private var newSessionRequested = false

    // Matomo gets confused with visits/sessions if the last event has the same timestamp as the
    // event with the new_visit parameter. Thus we store the timestamp of every tracked event and
    // delay all tracking events in case a new session was requested.
    private var lastEventTimeMs = 0L

    override fun startNewSession() {
        launch {
            mutex.withLock {
                newSessionRequested = true
                super.startNewSession()
            }
        }
    }

    override fun track(trackMe: TrackMe): org.matomo.sdk.Tracker {
        launch {
            var isNewSession = false
            mutex.withLock {
                // Determine if we have to delay this event in case a new session was requested.
                if (newSessionRequested) {
                    val now = System.currentTimeMillis()
                    val diff = now - lastEventTimeMs
                    if (diff < 1_000L) {
                        val msUntilNextSecond = 1_000L - (now % 1_000L)
                        // Give an additional 10ms buffer to delay to be sure we are on the next second
                        val delayMs = msUntilNextSecond + 10L

                        // Delaying the execution while holding the mutex is fine, because this
                        // will ensure that every following event will already have a different
                        // datetime string. The order of the events is kept, as the mutex is FIFO
                        delay(delayMs)
                    }
                }

                // Store this events time and let the super implementation handle the actual tracking
                lastEventTimeMs = System.currentTimeMillis()
                super.track(trackMe)

                // Test if this event did create a new session
                val sessionStartParam: String? = trackMe.get(QueryParams.SESSION_START)
                if (sessionStartParam == MATOMO_TRUE_VALUE) {
                    newSessionRequested = false
                    isNewSession = true
                }
            }

            // Finally, after the mutex is returned, the onNewSession callback may be triggered
            if (isNewSession) {
                onNewSession()
            }
        }

        return this
    }
}
