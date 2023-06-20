package de.taz.app.android.tracking

import android.content.Context
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import org.matomo.sdk.Matomo
import org.matomo.sdk.TrackerBuilder
import org.matomo.sdk.extra.TrackHelper


class MatomoTracker(applicationContext: Context) : Tracker {

    private val matomo = Matomo.getInstance(applicationContext)
    private val matomoTracker = TrackerBuilder
        .createDefault("https://gazpacho.taz.de/matomo.php", 113)
        //.setApplicationBaseUrl(??=)
        .build(matomo)
        .apply {
            //setDispatchGzipped()
            //setDispatchInterval()
        }

    override fun enable() {
        matomoTracker.isOptOut = false
    }

    override fun disable() {
        matomoTracker.isOptOut = true
    }

    override fun dispatch() {
        matomoTracker.dispatch()
    }

    override fun trackDownload(version: String) {
        TrackHelper.track().download().version(version).with(matomoTracker)
    }

    override fun trackAppIsBackgroundedEvent() {
        TrackHelper.track()
            .event("System", "Application Minimize")
            .with(matomoTracker)
    }

    override fun trackSystemNavigationBackEvent() {
        TrackHelper.track()
            .event("System", "Navigation Back")
            .with(matomoTracker)
    }

    override fun trackCoverflowScreen(pdfMode: Boolean) {
        val screenName = if (pdfMode) "/home/coverflow/pdf" else "/home/coverflow"
        TrackHelper.track().screen(screenName).title("Coverflow").with(matomoTracker)
    }

    override fun trackArchiveScreen(pdfMode: Boolean) {
        val screenName = if (pdfMode) "/home/archive/pdf" else "/home/archive"
        TrackHelper.track().screen(screenName).title("Archive").with(matomoTracker)
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

    private fun articlePath(article: Article): String {
        return "article/${article.key}|${article.mediaSyncId}"
    }

    override fun trackSectionScreen(issueKey: AbstractIssuePublication, section: Section) {
        val path = "/${issuePath(issueKey)}/${sectionPath(section)}"
        TrackHelper.track()
            .screen(path)
            .title(section.getHeaderTitle())
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

    override fun trackLoginHelpTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Help")
            .name("Help Button Tapped")
            .with(matomoTracker)
    }

    override fun trackLoginFormSubmitTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Submit")
            .name("Login Button Tapped")
            .with(matomoTracker)
    }

    override fun trackForgotPasswordTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Forgot Password")
            .name("Forgot Password Tapped")
            .with(matomoTracker)
    }

    override fun trackCancelTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Cancel")
            .name("Cancel Button Tapped")
            .with(matomoTracker)
    }

    override fun trackContinueTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Continue")
            .name("Continue Button Tapped")
            .with(matomoTracker)
    }

    override fun trackBackTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Back")
            .name("Back Button Tapped")
            .with(matomoTracker)
    }

    override fun trackAgreeTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Agree")
            .name("Agree Button Tapped")
            .with(matomoTracker)
    }

    override fun trackSubmitTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Submit")
            .name("Submit Button Tapped")
            .with(matomoTracker)
    }

    override fun trackSubscriptionFormValidationErrorEvent() {
        TrackHelper.track()
            .event("Form", "Validation Error")
            .name("Subscription Form Input Validation Error")
            .with(matomoTracker)
    }

    override fun trackSubscriptionFormSubmitEvent() {
        TrackHelper.track()
            .event("Form", "Submit")
            .name("Subscription Form Submit")
            .with(matomoTracker)
    }

    override fun trackSubscriptionTrialTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Trial Subscription")
            .name("Trial Subscription Tapped")
            .with(matomoTracker)
    }

    override fun trackSubscriptionSwitchTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Switch Subscription")
            .name("Switch Subscription Tapped")
            .with(matomoTracker)
    }

    override fun trackSubscriptionExtendTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Extend Subscription")
            .name("Extend Subscription Tapped")
            .with(matomoTracker)
    }

    override fun trackSubscriptionTermsTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Show Terms")
            .name("Show Terms Tapped")
            .with(matomoTracker)
    }

    override fun trackSubscriptionPrivacyPolicyTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Show Privacy Policy")
            .name("Show Privacy Policy Tapped")
            .with(matomoTracker)
    }

    override fun trackSubscriptionRevocationTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Show Revocation")
            .name("Show Revocation Terms Tapped")
            .with(matomoTracker)
    }

    override fun trackSubscriptionHelpTappedEvent() {
        TrackHelper.track()
            .event("Interaction", "Subscription Help")
            .name("Show Subscription Help Tapped")
            .with(matomoTracker)
    }

    override fun trackSubscriptionLoginCreateAccountSwitchTappedEvent(showLogin: Boolean) {
        TrackHelper.track()
            .event("Interaction", "Toggle Login/Create Account")
            .name("Toggle Login/Create Account Tapped")
            .value(if (showLogin) 1f else 0f)
            .with(matomoTracker)
    }

    override fun trackTogglePdfModeTappedEvent(switchToPdfMode: Boolean) {
        TrackHelper.track()
            .event("Interaction", "Toggle PDF Mode")
            .name("Toggle PDF Mode Button Tapped")
            .value(if (switchToPdfMode) 1f else 0f)
            .with(matomoTracker)
    }
}