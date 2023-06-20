package de.taz.app.android.tracking

import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.persistence.repository.AbstractIssuePublication

interface Tracker {

    fun enable()
    fun disable()

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
     * Send an Event when the user navigates back with the system device buttons or some back gesture.
     */
    fun trackSystemNavigationBackEvent()


    fun trackCoverflowScreen(pdfMode: Boolean)
    fun trackArchiveScreen(pdfMode: Boolean)
    fun trackBookmarkListScreen()
    fun trackSearchScreen()
    fun trackSettingsScreen()
    fun trackSectionScreen(issueKey: AbstractIssuePublication, section: Section)
    fun trackArticleScreen(
        issueKey: AbstractIssuePublication,
        sectionOperations: SectionOperations,
        article: Article
    )
    fun trackErrorReportScreen()

    fun trackWebViewScreen(htmlFile: String)
    fun trackLoginScreen()
    fun trackSubscriptionSwitchFormScreen()
    fun trackSubscriptionExtendFormScreen()
    fun trackSubscriptionTrialInfoScreen()
    fun trackSubscriptionTrialElapsedInfoScreen()
    fun trackSubscriptionPriceScreen()
    fun trackSubscriptionPersonalDataFormScreen()
    fun trackSubscriptionPaymentFormScreen()
    fun trackSubscriptionAccountLoginFormScreen()

    fun trackLoginHelpTappedEvent()
    fun trackLoginFormSubmitTappedEvent()
    fun trackForgotPasswordTappedEvent()

    fun trackCancelTappedEvent()
    fun trackContinueTappedEvent()
    fun trackBackTappedEvent()
    fun trackAgreeTappedEvent()
    fun trackSubmitTappedEvent()

    fun trackSubscriptionFormValidationErrorEvent()
    fun trackSubscriptionFormSubmitEvent()

    fun trackSubscriptionTrialTappedEvent()
    fun trackSubscriptionSwitchTappedEvent()
    fun trackSubscriptionExtendTappedEvent()
    fun trackSubscriptionTermsTappedEvent()
    fun trackSubscriptionPrivacyPolicyTappedEvent()
    fun trackSubscriptionRevocationTappedEvent()
    fun trackSubscriptionHelpTappedEvent()
    fun trackSubscriptionLoginCreateAccountSwitchTappedEvent(showLogin: Boolean)

    fun trackTogglePdfModeTappedEvent(switchToPdfMode: Boolean)
}