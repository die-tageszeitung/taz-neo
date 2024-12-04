package de.taz.app.android.tracking

import android.content.Context
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.api.models.Section
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.util.SingletonHolder

interface Tracker {

    companion object : SingletonHolder<Tracker, Context>(::createTrackerInstance)

    fun enable()
    fun disable()

    fun startNewSession()

    /**
     * Process all queued events in background thread
     */
    fun dispatch()

    /**
     * Send a download event for this app.
     * This only triggers an event once per app version.
     */
    fun trackDownload(version: String)


    /**
     * Send an Event when the App is backgrounded and the user is no longer interacting with the app.
     */
    fun trackAppIsBackgroundedEvent()

    /**
     * Has to be sent on every new tracking session if the user is authenticated.
     * A session must not track both [trackUserAuthenticatedState] and [trackUserAnonymousState].
     */
    fun trackUserAuthenticatedState()

    /**
     * Has to be sent on every new tracking session if the user is anonymous.
     * A session must not track both [trackUserAuthenticatedState] and [trackUserAnonymousState].
     */
    fun trackUserAnonymousState()

    /**
     * Send when a user logged in successfully.
     */
    fun trackUserLoginEvent()

    /**
     * Send when a user logged out successfully.
     */
    fun trackUserLogoutEvent()

    /**
     * Send when a users subscription elapsed.
     */
    fun trackUserSubscriptionElapsedEvent()

    /**
     * Send when a users subscription is renewed.
     */
    fun trackUserSubscriptionRenewedEvent()

    fun trackCoverflowScreen(pdfMode: Boolean)
    fun trackArchiveScreen(pdfMode: Boolean)
    fun trackBookmarkListScreen()
    fun trackSearchScreen()
    fun trackSettingsScreen()
    fun trackSectionScreen(issueKey: AbstractIssuePublication, section: Section)
    fun trackPdfPageScreen(issueKey: AbstractIssuePublication, pagina: String)
    fun trackArticleScreen(
        issueKey: AbstractIssuePublication,
        sectionOperations: SectionOperations,
        article: ArticleOperations
    )
    fun trackErrorReportScreen()
    fun trackWebViewScreen(htmlFile: String)

    fun trackLoginScreen()
    fun trackForgotPasswordScreen()
    fun trackSubscriptionSwitchFormScreen()
    fun trackSubscriptionExtendFormScreen()
    fun trackSubscriptionTrialInfoScreen()
    fun trackSubscriptionTrialElapsedInfoScreen()
    fun trackSubscriptionPersonalDataFormScreen()
    fun trackSubscriptionAccountLoginFormScreen()

    fun trackLoginHelpDialog()
    fun trackSubscriptionHelpDialog()
    fun trackSubscriptionElapsedDialog()
    fun trackAllowNotificationsDialog()
    fun trackIssueActionsDialog()
    fun trackIssueDatePickerDialog()
    fun trackTextSettingsDialog()
    fun trackSharingNotPossibleDialog()
    fun trackAutomaticDownloadDialog()
    fun trackPdfModeLoginHintDialog()
    fun trackConnectionErrorDialog()
    fun trackFatalErrorDialog()
    fun trackIssueDownloadErrorDialog()

    fun trackSubscriptionInquirySubmittedEvent()
    fun trackSubscriptionInquiryFormValidationErrorEvent()
    fun trackSubscriptionInquiryServerErrorEvent()
    fun trackSubscriptionInquiryNetworkErrorEvent()
    fun trackSubscriptionTrialConfirmedEvent()

    fun trackSwitchToPdfModeEvent()
    fun trackSwitchToMobileModeEvent()
    fun trackAddBookmarkEvent(articleFileName: String, mediaSyncId: Int?)
    fun trackRemoveBookmarkEvent(articleFileName: String, mediaSyncId: Int?)

    fun trackShareArticleEvent(article: ArticleOperations)
    fun trackShareArticleEvent(articleFileName: String, mediaSyncId: Int?)
    fun trackShareArticlePdfEvent(articleFileName: String, mediaSyncId: Int?)
    fun trackShareArticleLinkEvent(articleFileName: String, mediaSyncId: Int?)
    fun trackShareArticleTextEvent(articleFileName: String, mediaSyncId: Int?)
    fun trackShareMomentEvent(issueKey: AbstractIssuePublication)

    fun trackDrawerOpenEvent(dragged: Boolean)

    fun trackDrawerTapPageEvent()
    fun trackDrawerTapSectionEvent()
    fun trackDrawerTapArticleEvent()
    fun trackDrawerTapImprintEvent()
    fun trackDrawerTapMomentEvent()
    fun trackDrawerTapBookmarkEvent()
    fun trackDrawerTapPlayIssueEvent()
    fun trackDrawerToggleAllSectionsEvent()
    fun trackDrawerToggleSectionEvent()

    fun trackAudioPlayerPlayArticleEvent(articleOperations: ArticleOperations)
    fun trackAudioPlayerPlayPodcastEvent(
        issueKey: AbstractIssuePublication,
        title: String
    )
    fun trackAudioPlayerPlaySearchHitEvent(searchHit: SearchHit)
    fun trackAudioPlayerChangePlaySpeedEvent(playbackSpeed: Float)
    fun trackAudioPlayerMaximizeEvent()
    fun trackAudioPlayerMinimizeEvent()
    fun trackAudioPlayerCloseEvent()
    fun trackAudioPlayerSkipNextEvent()
    fun trackAudioPlayerSkipPreviousEvent()
    fun trackAudioPlayerSeekForwardSecondsEvent(seconds: Long)
    fun trackAudioPlayerSeekBackwardSecondsEvent(seconds: Long)
    fun trackAudioPlayerSeekForwardBreakEvent()
    fun trackAudioPlayerSeekBackwardBreakEvent()
    fun trackAudioPlayerSeekPositionEvent()
    fun trackAudioPlayerResumeEvent()
    fun trackAudioPlayerPauseEvent()
    fun trackAudioPlayerAutoplayEnableEvent()
    fun trackAudioPlayerAutoplayDisableEvent()

    fun trackCoachMarkShow(layoutResName: String)
    fun trackCoachMarkClose(layoutResName: String)

    fun trackTapToScrollSettingStatusEvent(enable: Boolean)

    fun trackIssueDownloadEvent(issueKey: AbstractIssuePublication)

    fun trackTestTrackingGoal()

    fun trackArticleColumnModeEnableEvent()
    fun trackArticleColumnModeDisableEvent()

    fun trackWidgetEnabledEvent()
    fun trackWidgetDisabledEvent()
}
