package de.taz.app.android.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.TazApplication
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.ActivityMainBinding
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.login.LoginActivity
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


const val MAIN_EXTRA_ARTICLE = "MAIN_EXTRA_ARTICLE"

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

    private val generalDataStore by lazy { GeneralDataStore.getInstance(application) }
    private val toastHelper by lazy { ToastHelper.getInstance(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authHelper = AuthHelper.getInstance(applicationContext)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                val isElapsedButWaiting = authHelper.elapsedButWaiting.get()
                val isElapsedFormAlreadySent = authHelper.elapsedFormAlreadySent.get()
                val elapsedAlreadyShown = (application as TazApplication).elapsedPopupAlreadyShown
                val isPdfMode = generalDataStore.pdfMode.get()
                val timesPdfShown = generalDataStore.tryPdfDialogCount.get()

                val elapsedBottomSheetConditions =
                    authHelper.isElapsed() && !isElapsedButWaiting && !elapsedAlreadyShown && !isElapsedFormAlreadySent
                when {
                    elapsedBottomSheetConditions -> showSubscriptionElapsedBottomSheet()
                    isPdfMode && !authHelper.isLoggedIn() && !authHelper.isElapsed() -> showLoggedOutDialog()
                    !isPdfMode && timesPdfShown < 1 -> showTryPdfDialog()
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

    private var doubleBackToExitPressedOnce = false
    override fun onBackPressed() {
        val homeFragment =
            supportFragmentManager.fragments.firstOrNull { it is HomeFragment } as? HomeFragment

        if (homeFragment?.onHome == true) {
            if (doubleBackToExitPressedOnce) {
                moveTaskToBack(true)
                finish()
            }

            this.doubleBackToExitPressedOnce = true
            toastHelper.showToast(getString(R.string.toast_click_again_to_exit))

            Handler(Looper.getMainLooper()).postDelayed({
                doubleBackToExitPressedOnce = false
            }, 2000)
        } else {
            showHome()
        }
    }
}
