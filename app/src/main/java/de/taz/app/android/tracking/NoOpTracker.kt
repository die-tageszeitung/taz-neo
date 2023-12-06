package de.taz.app.android.tracking

import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.Section
import de.taz.app.android.persistence.repository.AbstractIssuePublication

class NoOpTracker : Tracker {
    override fun enable() {}
    override fun disable() {}
    override fun startNewSession() {}
    override fun dispatch() {}
    override fun trackDownload(version: String) {}
    override fun trackAppIsBackgroundedEvent() {}
    override fun trackUserAuthenticatedState() {}
    override fun trackUserAnonymousState() {}
    override fun trackUserLoginEvent() {}
    override fun trackUserLogoutEvent() {}
    override fun trackUserSubscriptionElapsedEvent() {}
    override fun trackUserSubscriptionRenewedEvent() {}
    override fun trackCoverflowScreen(pdfMode: Boolean) {}
    override fun trackArchiveScreen(pdfMode: Boolean) {}
    override fun trackBookmarkListScreen() {}
    override fun trackSearchScreen() {}
    override fun trackSettingsScreen() {}
    override fun trackSectionScreen(issueKey: AbstractIssuePublication, section: Section) {}
    override fun trackPdfPageScreen(issueKey: AbstractIssuePublication, pagina: String) {}
    override fun trackArticleScreen(issueKey: AbstractIssuePublication, sectionOperations: SectionOperations, article: Article) {}
    override fun trackErrorReportScreen() {}
    override fun trackWebViewScreen(htmlFile: String) {}
    override fun trackLoginScreen() {}
    override fun trackForgotPasswordScreen() {}
    override fun trackSubscriptionSwitchFormScreen() {}
    override fun trackSubscriptionExtendFormScreen() {}
    override fun trackSubscriptionTrialInfoScreen() {}
    override fun trackSubscriptionTrialElapsedInfoScreen() {}
    override fun trackSubscriptionPersonalDataFormScreen() {}
    override fun trackSubscriptionAccountLoginFormScreen() {}
    override fun trackLoginHelpDialog() {}
    override fun trackSubscriptionHelpDialog() {}
    override fun trackSubscriptionElapsedDialog() {}
    override fun trackAllowNotificationsDialog() {}
    override fun trackIssueActionsDialog() {}
    override fun trackIssueDatePickerDialog() {}
    override fun trackTextSettingsDialog() {}
    override fun trackSharingNotPossibleDialog() {}
    override fun trackAutomaticDownloadDialog() {}
    override fun trackPdfModeLoginHintDialog() {}
    override fun trackConnectionErrorDialog() {}
    override fun trackFatalErrorDialog() {}
    override fun trackIssueDownloadErrorDialog() {}
    override fun trackSubscriptionInquirySubmittedEvent() {}
    override fun trackSubscriptionInquiryFormValidationErrorEvent() {}
    override fun trackSubscriptionInquiryServerErrorEvent() {}
    override fun trackSubscriptionInquiryNetworkErrorEvent() {}
    override fun trackSubscriptionTrialConfirmedEvent() {}
    override fun trackSwitchToPdfModeEvent() {}
    override fun trackSwitchToMobileModeEvent() {}
    override fun trackAddBookmarkEvent(articleFileName: String, mediaSyncId: Int?) {}
    override fun trackRemoveBookmarkEvent(articleFileName: String, mediaSyncId: Int?) {}
    override fun trackShareArticleEvent(article: Article) {}
    override fun trackShareArticleEvent(articleStub: ArticleStub) {}
    override fun trackShareSearchHitEvent(onlineLink: String) {}
    override fun trackShareMomentEvent(issueKey: AbstractIssuePublication) {}
    override fun trackDrawerOpenEvent(dragged: Boolean) {}
    override fun trackDrawerTapPageEvent() {}
    override fun trackDrawerTapSectionEvent() {}
    override fun trackDrawerTapArticleEvent() {}
    override fun trackDrawerTapImprintEvent() {}
    override fun trackDrawerTapMomentEvent() {}
    override fun trackDrawerTapBookmarkEvent() {}
    override fun trackDrawerTapPlayIssueEvent() {}
    override fun trackDrawerToggleAllSectionsEvent() {}
    override fun trackDrawerToggleSectionEvent() {}
    override fun trackAudioPlayerPlayArticleEvent(articleAudio: Article) {}
    override fun trackAudioPlayerPlayPodcastEvent(issueKey: AbstractIssuePublication, title: String) {}
    override fun trackAudioPlayerChangePlaySpeedEvent(playbackSpeed: Float) {}
    override fun trackAudioPlayerMaximizeEvent() {}
    override fun trackAudioPlayerMinimizeEvent() {}
    override fun trackAudioPlayerCloseEvent() {}
    override fun trackAudioPlayerSkipNextEvent() {}
    override fun trackAudioPlayerSkipPreviousEvent() {}
    override fun trackAudioPlayerSeekForwardSecondsEvent(seconds: Long) {}
    override fun trackAudioPlayerSeekBackwardSecondsEvent(seconds: Long) {}
    override fun trackAudioPlayerSeekForwardBreakEvent() {}
    override fun trackAudioPlayerSeekBackwardBreakEvent() {}
    override fun trackAudioPlayerSeekPositionEvent() {}
    override fun trackAudioPlayerResumeEvent() {}
    override fun trackAudioPlayerPauseEvent() {}
    override fun trackAudioPlayerAutoplayEnableEvent() {}
    override fun trackAudioPlayerAutoplayDisableEvent() {}
    override fun trackCoachMarkShow(layoutResName: String) {}
    override fun trackCoachMarkClose(layoutResName: String) {}
}