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
    override fun trackSystemNavigationBackEvent() {}
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
    override fun trackSubscriptionSwitchFormScreen() {}
    override fun trackSubscriptionExtendFormScreen() {}
    override fun trackSubscriptionTrialInfoScreen() {}
    override fun trackSubscriptionTrialElapsedInfoScreen() {}
    override fun trackSubscriptionPriceScreen() {}
    override fun trackSubscriptionPersonalDataFormScreen() {}
    override fun trackSubscriptionPaymentFormScreen() {}
    override fun trackSubscriptionAccountLoginFormScreen() {}
    override fun trackLoginHelpTappedEvent() {}
    override fun trackLoginFormSubmitTappedEvent() {}
    override fun trackForgotPasswordTappedEvent() {}
    override fun trackCancelTappedEvent() {}
    override fun trackContinueTappedEvent() {}
    override fun trackBackTappedEvent() {}
    override fun trackAgreeTappedEvent() {}
    override fun trackSubmitTappedEvent() {}
    override fun trackSubscriptionFormValidationErrorEvent() {}
    override fun trackSubscriptionFormSubmitEvent() {}
    override fun trackSubscriptionTrialTappedEvent() {}
    override fun trackSubscriptionSwitchTappedEvent() {}
    override fun trackSubscriptionExtendTappedEvent() {}
    override fun trackSubscriptionTermsTappedEvent() {}
    override fun trackSubscriptionPrivacyPolicyTappedEvent() {}
    override fun trackSubscriptionRevocationTappedEvent() {}
    override fun trackSubscriptionHelpTappedEvent() {}
    override fun trackSubscriptionLoginCreateAccountSwitchTappedEvent(showLogin: Boolean) {}
    override fun trackTogglePdfModeTappedEvent(switchToPdfMode: Boolean) {}
}