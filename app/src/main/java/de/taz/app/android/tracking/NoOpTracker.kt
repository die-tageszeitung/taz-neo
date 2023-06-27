package de.taz.app.android.tracking

import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.persistence.repository.AbstractIssuePublication

class NoOpTracker : Tracker {
    override fun enable() {}
    override fun disable() {}
    override fun dispatch() {}
    override fun trackDownload(version: String) {}
    override fun trackAppIsBackgroundedEvent() {}
    override fun trackCoverflowScreen(pdfMode: Boolean) {}
    override fun trackArchiveScreen(pdfMode: Boolean) {}
    override fun trackBookmarkListScreen() {}
    override fun trackSearchScreen() {}
    override fun trackSettingsScreen() {}
    override fun trackSectionScreen(issueKey: AbstractIssuePublication, section: Section) {}
    override fun trackArticleScreen(issueKey: AbstractIssuePublication, sectionOperations: SectionOperations, article: Article) {}
    override fun trackErrorReportScreen() {}
    override fun trackWebViewScreen(htmlFile: String) {}
    override fun trackLoginScreen() {}
    override fun trackForgotPasswordScreen() {}
    override fun trackSubscriptionSwitchFormScreen() {}
    override fun trackSubscriptionExtendFormScreen() {}
    override fun trackSubscriptionTrialInfoScreen() {}
    override fun trackSubscriptionTrialElapsedInfoScreen() {}
    override fun trackSubscriptionPriceScreen() {}
    override fun trackSubscriptionPersonalDataFormScreen() {}
    override fun trackSubscriptionPaymentFormScreen() {}
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
    override fun trackPdfModeSwitchHintDialog() {}
    override fun trackConnectionErrorDialog() {}
    override fun trackFatalErrorDialog() {}
    override fun trackIssueDownloadErrorDialog() {}
    override fun trackSubscriptionInquirySubmittedEvent() {}
    override fun trackSubscriptionInquiryFormValidationErrorEvent() {}
    override fun trackSubscriptionInquiryServerErrorEvent() {}
    override fun trackSubscriptionInquiryNetworkErrorEvent() {}
    override fun trackTogglePdfModeTappedEvent(switchToPdfMode: Boolean) {}
}