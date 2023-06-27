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
        .build(matomo)

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
            .event("Application", "Minimize")
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
            .event("Dialog", "Login Help")
            .with(matomoTracker)
    }

    override fun trackSubscriptionHelpDialog() {
        TrackHelper.track()
            .event("Dialog", "Subscription Help")
            .with(matomoTracker)
    }

    override fun trackSubscriptionElapsedDialog() {
        TrackHelper.track()
            .event("Dialog", "Subscription Elapsed")
            .with(matomoTracker)
    }

    override fun trackAllowNotificationsDialog() {
        TrackHelper.track()
            .event("Dialog", "Allow Notifications Info")
            .with(matomoTracker)
    }

    override fun trackIssueActionsDialog() {
        TrackHelper.track()
            .event("Dialog", "Issue Actions")
            .with(matomoTracker)
    }

    override fun trackIssueDatePickerDialog() {
        TrackHelper.track()
            .event("Dialog", "Issue Date Picker")
            .with(matomoTracker)
    }

    override fun trackTextSettingsDialog() {
        TrackHelper.track()
            .event("Dialog", "Text Settings")
            .with(matomoTracker)
    }

    override fun trackSharingNotPossibleDialog() {
        TrackHelper.track()
            .event("Dialog", "Sharing Not Possible")
            .with(matomoTracker)
    }

    override fun trackAutomaticDownloadDialog() {
        TrackHelper.track()
            .event("Dialog", "Automatic Download Choice")
            .with(matomoTracker)
    }

    override fun trackPdfModeLoginHintDialog() {
        TrackHelper.track()
            .event("Dialog", "PDF Mode Login Hint")
            .with(matomoTracker)
    }

    override fun trackPdfModeSwitchHintDialog() {
        TrackHelper.track()
            .event("Dialog", "PDF Mode Switch Hint")
            .with(matomoTracker)
    }

    override fun trackConnectionErrorDialog() {
        TrackHelper.track()
            .event("Dialog", "Connection Error")
            .with(matomoTracker)
    }

    override fun trackFatalErrorDialog() {
        TrackHelper.track()
            .event("Dialog", "Fatal Error")
            .with(matomoTracker)
    }

    override fun trackIssueDownloadErrorDialog() {
        TrackHelper.track()
            .event("Dialog", "Issue Download Error")
            .with(matomoTracker)
    }

    override fun trackSubscriptionInquirySubmittedEvent() {
        TrackHelper.track()
            .event("Subscription", "Inquiry Submitted")
            .with(matomoTracker)
    }

    override fun trackSubscriptionInquiryFormValidationErrorEvent() {
        TrackHelper.track()
            .event("Subscription", "Inquiry Form Validation Error")
            .with(matomoTracker)
    }

    override fun trackSubscriptionInquiryServerErrorEvent() {
        TrackHelper.track()
            .event("Subscription", "Inquiry Server Error")
            .with(matomoTracker)
    }

    override fun trackSubscriptionInquiryNetworkErrorEvent() {
        TrackHelper.track()
            .event("Subscription", "Inquiry Network Error")
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