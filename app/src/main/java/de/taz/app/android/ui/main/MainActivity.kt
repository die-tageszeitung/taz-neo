package de.taz.app.android.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.TazApplication
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.audioPlayer.AudioPlayerViewController
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.ActivityMainBinding
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.bottomSheet.AllowNotificationsBottomSheetFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.login.LoginActivity
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


const val MAIN_EXTRA_ARTICLE = "MAIN_EXTRA_ARTICLE"
private const val DOUBLE_BACK_TO_EXIT_INTERVAL = 2000L

@Mockable
class MainActivity : ViewBindingActivity<ActivityMainBinding>() {

    companion object {
        const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"
        fun start(context: Context, flags: Int = 0, issuePublication: IssuePublication? = null) {
            val intent = Intent(context, MainActivity::class.java)
            intent.flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TOP
            issuePublication?.let { intent.putExtra(KEY_ISSUE_PUBLICATION, issuePublication) }
            ContextCompat.startActivity(context, intent, null)
        }
    }

    private lateinit var authHelper: AuthHelper
    private lateinit var downloadDataStore: DownloadDataStore
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    @Suppress("unused")
    private val audioPlayerViewController = AudioPlayerViewController(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authHelper = AuthHelper.getInstance(applicationContext)
        downloadDataStore = DownloadDataStore.getInstance(applicationContext)
        generalDataStore = GeneralDataStore.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)
        tracker = Tracker.getInstance(applicationContext)


        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val isElapsedButWaiting = authHelper.elapsedButWaiting.get()
                val isElapsedFormAlreadySent = authHelper.elapsedFormAlreadySent.get()
                val elapsedAlreadyShown = (application as TazApplication).elapsedPopupAlreadyShown
                val isPdfMode = generalDataStore.pdfMode.get()
                val timesPdfShown = generalDataStore.tryPdfDialogCount.get()
                val allowNotificationsDoNotShowAgain = generalDataStore.allowNotificationsDoNotShowAgain.get()
                val allowNotificationsLastTimeShown = generalDataStore.allowNotificationsLastTimeShown.get()
                val allowNotificationsShownLastMonth =
                    DateHelper.stringToDate(allowNotificationsLastTimeShown)?.let { lastShown ->
                        lastShown > DateHelper.lastTenDays()
                    } ?: false

                val elapsedBottomSheetConditions =
                    authHelper.isElapsed() && !isElapsedButWaiting && !elapsedAlreadyShown && !isElapsedFormAlreadySent

                val allowNotificationsBottomSheetConditions =
                    !checkNotificationsAllowed() && !allowNotificationsDoNotShowAgain && !allowNotificationsShownLastMonth && BuildConfig.IS_NON_FREE && !BuildConfig.IS_LMD

                when {
                    elapsedBottomSheetConditions -> showSubscriptionElapsedBottomSheet()
                    isPdfMode && !authHelper.isLoggedIn() && !authHelper.isElapsed() -> showLoggedOutDialog()
                    !isPdfMode && timesPdfShown < 1 -> showTryPdfDialog()
                    allowNotificationsBottomSheetConditions -> showAllowNotificationsBottomSheet()
                    else -> Unit // do nothing else
                }
            }
        }

        // create WebView then throw it away so later instantiations are faster
        // otherwise we have lags in the [CoverFlowFragment]
        WebView(this)
    }

    override fun onResume() {
        super.onResume()
        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.Home
        )
    }

    override fun onStop() {
        loggedOutDialog?.dismiss()
        tryPdfDialog?.dismiss()
        super.onStop()
    }

    private var loggedOutDialog: AlertDialog? = null
    private suspend fun showLoggedOutDialog() {
        loggedOutDialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setMessage(R.string.pdf_mode_better_to_be_logged_in_hint)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton(R.string.login_button) { dialog, _ ->
                startActivityForResult(
                    Intent(this@MainActivity, LoginActivity::class.java),
                    ACTIVITY_LOGIN_REQUEST_CODE
                )
                dialog.dismiss()
            }
            .create()

        loggedOutDialog?.show()
        tracker.trackPdfModeLoginHintDialog()
    }

    private var tryPdfDialog: AlertDialog? = null
    private suspend fun showTryPdfDialog() {
        val timesPdfShown = generalDataStore.tryPdfDialogCount.get()
        tryPdfDialog = MaterialAlertDialogBuilder(this)
            .setView(R.layout.dialog_try_pdf)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                applicationScope.launch(Dispatchers.Main) {
                    generalDataStore.tryPdfDialogCount.set(timesPdfShown + 1)
                    dialog.dismiss()
                }
            }
            .create()

        tryPdfDialog?.show()
        tracker.trackPdfModeSwitchHintDialog()
        tryPdfDialog?.findViewById<ImageButton>(R.id.button_close)?.setOnClickListener {
            applicationScope.launch(Dispatchers.Main) {
                generalDataStore.tryPdfDialogCount.set(timesPdfShown + 1)
                tryPdfDialog?.dismiss()
            }
        }
    }

    fun showHome() {
        runOnUiThread {
            supportFragmentManager.popBackStackImmediate(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
            val homeFragment =
                supportFragmentManager.fragments.firstOrNull { it is HomeFragment } as? HomeFragment
            val coverFlowFragment =
                homeFragment?.childFragmentManager?.fragments?.firstOrNull { it is CoverflowFragment } as? CoverflowFragment
            this.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                currentItem -= 1
            }
            coverFlowFragment?.skipToHome()
        }
    }

    private fun showSubscriptionElapsedBottomSheet() {
        (application as TazApplication).elapsedPopupAlreadyShown = true
        SubscriptionElapsedBottomSheetFragment().show(
            supportFragmentManager,
            "showSubscriptionElapsed"
        )
    }

    private fun showAllowNotificationsBottomSheet() {
        AllowNotificationsBottomSheetFragment().show(
            supportFragmentManager,
            "showSubscriptionElapsed"
        )
    }

    private var doubleBackToExitPressedOnce = false
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (audioPlayerViewController.onBackPressed()) {
            return
        }

        val homeFragment =
            supportFragmentManager.fragments.firstOrNull { it is HomeFragment } as? HomeFragment

        if (homeFragment?.onHome == true) {
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
    }

    private suspend fun checkNotificationsAllowed(): Boolean {
        val systemAllows = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val appAllows = downloadDataStore.notificationsEnabled.get()
        return systemAllows && appAllows
    }
}
