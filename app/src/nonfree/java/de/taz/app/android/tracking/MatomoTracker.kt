package de.taz.app.android.tracking

import android.content.Context
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.Section
import de.taz.app.android.audioPlayer.ArticleAudio
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.matomo.sdk.Matomo
import org.matomo.sdk.QueryParams
import org.matomo.sdk.TrackMe
import org.matomo.sdk.TrackerBuilder
import org.matomo.sdk.extra.TrackHelper
import kotlin.coroutines.CoroutineContext

// region: Event Categories
private const val CATEGORY_APPLICATION = "Application"
private const val CATEGORY_USER_AUTH = "User Authentication"
private const val CATEGORY_AUTH_STATUS = "Authentication Status"
private const val CATEGORY_SUBSCRIPTION_STATUS = "Subscription Status"
private const val CATEGORY_DIALOG = "Dialog"
private const val CATEGORY_SUBSCRIPTION = "Subscription"
private const val CATEGORY_APPMODE = "AppMode"
private const val CATEGORY_BOOKMARKS = "Bookmarks"
private const val CATEGORY_SHARE = "Share"
private const val CATEGORY_DRAWER = "Drawer"
private const val CATEGORY_AUDIO_PLAYER = "Audio Player"
// endregion

private const val SESSION_TIMEOUT_MS = 2 * 60 * 60 * 1_000 // 2h

class MatomoTracker(applicationContext: Context) : Tracker, CoroutineScope {

    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Main

    private val log by Log

    val authHelper = AuthHelper.getInstance(applicationContext)

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

    private fun onNewSession() {
        launch {
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
            .event(CATEGORY_AUTH_STATUS, "Authenticated")
            .with(matomoTracker)
    }

    override fun trackUserAnonymousState() {
        TrackHelper.track()
            .event(CATEGORY_AUTH_STATUS, "Anonymous")
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
            .event(CATEGORY_SUBSCRIPTION_STATUS, "Renewed")
            .with(matomoTracker)
    }

    override fun trackCoverflowScreen(pdfMode: Boolean) {
        val screenName = if (pdfMode) "/home/coverflow/pdf" else "/home/coverflow/app"
        val screenTitle = if (pdfMode) "Coverflow PDF" else "Coverflow App"
        TrackHelper.track().screen(screenName).title(screenTitle).with(matomoTracker)
    }

    override fun trackArchiveScreen(pdfMode: Boolean) {
        val screenName = if (pdfMode) "/home/archive/pdf" else "/home/archive/app"
        val screenTitle = if (pdfMode) "Archive PDF" else "Archive App"
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
        return "article/$articleFileName|$mediaSyncSuffix"
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

    override fun trackSubscriptionPriceScreen() {
        TrackHelper.track()
            .screen("/subscription/price_selection")
            .title("Subscription Price Selection")
            .with(matomoTracker)
    }

    override fun trackSubscriptionPersonalDataFormScreen() {
        TrackHelper.track()
            .screen("/subscription/personal_data_form")
            .title("Subscription Personal Data Form")
            .with(matomoTracker)
    }

    override fun trackSubscriptionPaymentFormScreen() {
        TrackHelper.track()
            .screen("/subscription/payment_form")
            .title("Subscription Payment Form")
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

    override fun trackPdfModeSwitchHintDialog() {
        TrackHelper.track()
            .event(CATEGORY_DIALOG, "PDF Mode Switch Hint")
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

    override fun trackAudioPlayerPlayArticleEvent(articleAudio: ArticleAudio) {
        TrackHelper.track()
            .event(CATEGORY_AUDIO_PLAYER, "Play Article")
            .name(articlePath(articleAudio.article.key, articleAudio.article.mediaSyncId))
            .with(matomoTracker)
    }

}

/**
 * Wrapper around [org.matomo.sdk.Tracker] calling [onNewSession] when the first event of a new session is sent.
 */
private class SessionAwareTracker(
    matomo: Matomo,
    config: TrackerBuilder,
    private val onNewSession: () -> Unit
) : org.matomo.sdk.Tracker(matomo, config) {

    companion object {
        private const val MATOMO_TRUE_VALUE: String = "1"
    }

    init {
        requireNotNull(config.applicationBaseUrl) { "TrackerBuilder must contain a valid applicationBaseUrl" }
    }

    // Circuit breaker to prevent from infinite recursion in case of onNewSession calling track() with another event
    private var isInNewSessionHandling = false

    override fun track(trackMe: TrackMe): org.matomo.sdk.Tracker {
        super.track(trackMe)

        val sessionStartParam: String? = trackMe.get(QueryParams.SESSION_START)
        if (sessionStartParam == MATOMO_TRUE_VALUE && !isInNewSessionHandling) {
            isInNewSessionHandling = true
            onNewSession()
            isInNewSessionHandling = false
        }
        return this
    }
}