package de.taz.app.android.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.APP_SESSION_TIMEOUT_MS
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.appReview.ReviewFlow
import de.taz.app.android.audioPlayer.AudioPlayerViewController
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.dataStore.CoachMarkDataStore
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.ActivityMainBinding
import de.taz.app.android.monkey.disableActivityAnimations
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.singletons.WidgetHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.SuccessfulLoginAction
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.ui.issueViewer.IssueViewerWrapperFragment
import de.taz.app.android.ui.login.LoginBottomSheetFragment
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment.Companion.shouldShowSubscriptionElapsedDialog
import de.taz.app.android.ui.pdfViewer.PdfPagerWrapperFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DOUBLE_BACK_TO_EXIT_INTERVAL = 2000L


class MainActivity : ViewBindingActivity<ActivityMainBinding>(), SuccessfulLoginAction {

    companion object {
        const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"
        const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE"

        fun start(
            context: Context,
            flags: Int = 0,
            issuePublication: IssuePublication? = null,
        ) {
            val intent = Intent(context, MainActivity::class.java)
            intent.flags = flags or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            issuePublication?.let { intent.putExtra(KEY_ISSUE_PUBLICATION, issuePublication) }
            ContextCompat.startActivity(context, intent, null)
        }

        fun start(
            context: Context,
            issuePublication: IssuePublicationWithPages,
            displayableKey: String,
        ) {
            ContextCompat.startActivity(
                context,
                newIntent(context, issuePublication, displayableKey),
                null
            )
        }

        fun start(
            context: Context,
            issuePublication: IssuePublication,
            displayableKey: String,
        ) {
            ContextCompat.startActivity(
                context,
                newIntent(context, issuePublication, displayableKey),
                null
            )
        }

        fun newIntent(
            packageContext: Context,
            issuePublication: IssuePublicationWithPages,
            displayableKey: String,
        ) = Intent(packageContext, MainActivity::class.java).apply {
            putExtra(KEY_ISSUE_PUBLICATION, issuePublication)
            putExtra(KEY_DISPLAYABLE, displayableKey)
        }

        fun newIntent(
            packageContext: Context,
            issuePublication: IssuePublication,
            displayableKey: String,
        ) = Intent(packageContext, MainActivity::class.java).apply {
            putExtra(KEY_ISSUE_PUBLICATION, issuePublication)
            putExtra(KEY_DISPLAYABLE, displayableKey)
        }
    }

    private lateinit var authHelper: AuthHelper
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var downloadDataStore: DownloadDataStore
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var coachMarkDataStore: CoachMarkDataStore
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    @Suppress("unused")
    private val audioPlayerViewController = AudioPlayerViewController(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authHelper = AuthHelper.getInstance(applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(applicationContext)
        coachMarkDataStore = CoachMarkDataStore.getInstance(applicationContext)
        downloadDataStore = DownloadDataStore.getInstance(applicationContext)
        generalDataStore = GeneralDataStore.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)
        tracker = Tracker.getInstance(applicationContext)

        disableActivityAnimations()

        if (savedInstanceState == null) {
            checkForIntentAndHandle()
        }
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    handlePendingDialogs()
                }
                launch {
                    val newAppSessionCount = handleAppSession()
                    if (newAppSessionCount != null) {
                        maybeStartReviewFlow(newAppSessionCount)
                        bookmarkRepository.checkForSynchronizedBookmarksIfEnabled()
                    }
                }
            }
        }

        // create WebView then throw it away so later instantiations are faster
        // otherwise we have lags in the [CoverFlowFragment]
        WebView(this)
    }

    override fun onResume() {
        super.onResume()
        // Ensure the widget is updated on app start – so it will always show the latest issue:
        WidgetHelper.updateWidget(applicationContext)
    }

    override fun onStop() {
        loggedOutDialog?.dismiss()
        loggedOutDialog = null
        super.onStop()
    }

    /**
     * This function will try to show any pending information dialogs to the user.
     * It must be called during the [MainActivity]s STARTED lifecycle.
     */
    private suspend fun handlePendingDialogs() {
        // This coroutine is restarted when the Activity lifecycle is going through STARTED.
        // We have to prevent multiple dialogs from overlapping, as the STARTED lifecycle
        // might be triggered again if for example the Data Policy Activity was shown from the
        // TrackingConsentFragment Bottom Sheet.
        // In that case the conditions would be different and an additional dialog might be shown.
        if (isShowingDialog()) {
            return
        }

        val isPdfMode = generalDataStore.pdfMode.get()
        val allowNotificationsDoNotShowAgain =
            generalDataStore.allowNotificationsDoNotShowAgain.get()
        val allowNotificationsLastTimeShown = generalDataStore.allowNotificationsLastTimeShown.get()
        val allowNotificationsShownLastMonth =
            DateHelper.stringToDate(allowNotificationsLastTimeShown)?.let { lastShown ->
                lastShown > DateHelper.lastTenDays()
            } ?: false

        val elapsedBottomSheetConditions = authHelper.shouldShowSubscriptionElapsedDialog()

        val allowNotificationsBottomSheetConditions =
            !checkNotificationsAllowed() && !allowNotificationsDoNotShowAgain && !allowNotificationsShownLastMonth && BuildConfig.IS_NON_FREE && !BuildConfig.IS_LMD

        val trackingOptInBottomSheetConditions =
            !generalDataStore.hasBeenAskedForTrackingConsent.get() && BuildConfig.IS_NON_FREE && !BuildConfig.IS_LMD

        when {
            elapsedBottomSheetConditions -> showSubscriptionElapsedBottomSheet()
            isPdfMode && !authHelper.isLoggedIn() && !authHelper.isElapsed() -> showLoggedOutDialog()
            trackingOptInBottomSheetConditions -> showTrackingConsentBottomSheet(
                allowNotificationsBottomSheetConditions
            )
            allowNotificationsBottomSheetConditions -> showAllowNotificationsBottomSheet()
            else -> Unit // do nothing else
        }
    }

    /**
     * This function will start a new app session if the [APP_SESSION_TIMEOUT_MS] has passed.
     * It must be called during the [MainActivity]s STARTED lifecycle.
     *
     * @return the new app session count if a new session was started,
     *         or null otherwise
     */
    private suspend fun handleAppSession(): Long? {
        val nowMs = System.currentTimeMillis()
        val lastMainActivityUsageTimeMs = generalDataStore.lastMainActivityUsageTimeMs.get()

        generalDataStore.lastMainActivityUsageTimeMs.set(nowMs)

        if (lastMainActivityUsageTimeMs + APP_SESSION_TIMEOUT_MS < nowMs) {
            val appSessionCount = generalDataStore.appSessionCount.get() + 1L
            generalDataStore.appSessionCount.set(appSessionCount)
            // reset the coach mark count:
            coachMarkDataStore.coachMarksShownInSession.set(0)
            return appSessionCount
        }
        return null
    }

    private var loggedOutDialog: AlertDialog? = null
    private suspend fun showLoggedOutDialog() {
        loggedOutDialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setMessage(R.string.pdf_mode_better_to_be_logged_in_hint)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton(R.string.login_button) { dialog, _ ->
                LoginBottomSheetFragment.newInstance().show(supportFragmentManager, LoginBottomSheetFragment.TAG)
                dialog.dismiss()
            }
            .create()

        loggedOutDialog?.show()
        tracker.trackPdfModeLoginHintDialog()
    }

    fun showHome() {
        runOnUiThread {
            val homeFragment =
                supportFragmentManager.fragments.firstOrNull { it is HomeFragment } as? HomeFragment
            val coverFlowFragment =
                homeFragment?.childFragmentManager?.fragments?.firstOrNull { it is CoverflowFragment } as? CoverflowFragment

            if(supportFragmentManager.fragments.last { it.isVisible } is HomeFragment) {
                coverFlowFragment?.skipToHome()
                this.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                    currentItem -= 1
                }
            } else {
                supportFragmentManager.popBackStackImmediate(
                    null,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE
                )
            }
        }
    }

    private fun showSubscriptionElapsedBottomSheet() {
        SubscriptionElapsedBottomSheetFragment.showSingleInstance(supportFragmentManager)
    }

    private fun showAllowNotificationsBottomSheet() {
        if (supportFragmentManager.findFragmentByTag(AllowNotificationsBottomSheetFragment.TAG) == null) {
            AllowNotificationsBottomSheetFragment().show(
                supportFragmentManager,
                AllowNotificationsBottomSheetFragment.TAG
            )
        }
    }

    private fun showTrackingConsentBottomSheet(showAllowNotifications: Boolean) {
        if (supportFragmentManager.findFragmentByTag(TrackingConsentBottomSheet.TAG) == null) {
            TrackingConsentBottomSheet.newInstance(showAllowNotifications).show(
                supportFragmentManager,
                TrackingConsentBottomSheet.TAG
            )
        }
    }

    private fun isShowingDialog(): Boolean {
        return loggedOutDialog?.isShowing == true
                || supportFragmentManager.findFragmentByTag(SubscriptionElapsedBottomSheetFragment.TAG) != null
                || supportFragmentManager.findFragmentByTag(AllowNotificationsBottomSheetFragment.TAG) != null
                || supportFragmentManager.findFragmentByTag(TrackingConsentBottomSheet.TAG) != null
    }

    private var doubleBackToExitPressedOnce = false


    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (audioPlayerViewController.onBackPressed()) {
            return
        }

        val currentFragment =
            supportFragmentManager.fragments.last()

        when (currentFragment) {
            is HomeFragment ->
                if (currentFragment.onHome) {
                    if (doubleBackToExitPressedOnce) {
                        moveTaskToBack(true)
                        finish()
                    }

                    this.doubleBackToExitPressedOnce = true
                    toastHelper.showToast(getString(R.string.toast_click_again_to_exit))

                    lifecycleScope.launch {
                        delay(DOUBLE_BACK_TO_EXIT_INTERVAL)
                        doubleBackToExitPressedOnce = false
                    }
                } else {
                    showHome()
                }

            is BackFragment ->
                if (currentFragment.onBackPressed())
                    return
                else
                    if (!supportFragmentManager.popBackStackImmediate())
                        super.onBackPressed()

            else ->
                if (!supportFragmentManager.popBackStackImmediate())
                    super.onBackPressed()
        }
    }

    private suspend fun checkNotificationsAllowed(): Boolean {
        val systemAllows = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val appAllows = downloadDataStore.notificationsEnabled.get()
        return systemAllows && appAllows
    }

    /**
     * Triggers a [ReviewFlow] on certain app sessions.
     * The triggers are defined by the UX team as specified on the ticket.
     * This function must only be called once per app session.
     * Only logged in users should be asked for reviews
     */
    private fun maybeStartReviewFlow(appSessionCount: Long) {
        if (appSessionCount == 3L || appSessionCount == 6L || appSessionCount == 10L || appSessionCount % 25L == 0L) {
            lifecycleScope.launch {
                if (authHelper.isLoggedIn()) {
                    val reviewFlow = ReviewFlow.createInstance()
                    reviewFlow.tryStartReviewFlow(this@MainActivity)
                }
            }
        }
    }

    /**
     * Check if we have an [IssuePublication] (or [IssuePublicationWithPages])
     * and a [KEY_DISPLAYABLE] in our intent.
     * If so, open the corresponding activity and show the displayable
     */
    private fun checkForIntentAndHandle() {
        val issuePublication =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    KEY_ISSUE_PUBLICATION,
                    AbstractIssuePublication::class.java
                )
            }  else {
                intent.getParcelableExtra<AbstractIssuePublication>(KEY_ISSUE_PUBLICATION)
            }
        val displayableKey = intent.getStringExtra(KEY_DISPLAYABLE)
        if (issuePublication != null && displayableKey != null) {
            when (issuePublication) {
                is IssuePublication -> {
                    // show issue viewer activity with intent:
                    supportFragmentManager.commit {
                        replace(
                            R.id.main_content_fragment_placeholder,
                            IssueViewerWrapperFragment.newInstance(issuePublication, displayableKey)
                        )
                        addToBackStack(null)
                    }
                }

                is IssuePublicationWithPages -> {
                    // show pdf pager activity with intent:
                    supportFragmentManager.commit {
                        replace(
                            R.id.main_content_fragment_placeholder,
                            PdfPagerWrapperFragment.newInstance(issuePublication, displayableKey)
                        )
                        addToBackStack(null)
                    }
                }
            }
        }
    }

    override fun onLogInSuccessful(articleName: String?) {
        supportFragmentManager.fragments.filter { it is SuccessfulLoginAction }.forEach {
            (it as SuccessfulLoginAction).onLogInSuccessful(articleName)
        }
    }
}
