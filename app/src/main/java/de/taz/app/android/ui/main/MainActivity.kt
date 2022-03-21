package de.taz.app.android.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.widget.ImageButton
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.ActivityMainBinding
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.login.LoginActivity
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


const val MAIN_EXTRA_TARGET = "MAIN_EXTRA_TARGET"
const val MAIN_EXTRA_TARGET_HOME = "MAIN_EXTRA_TARGET_HOME"
const val MAIN_EXTRA_TARGET_ARTICLE = "MAIN_EXTRA_TARGET_ARTICLE"
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

    val issueFeedViewModel: IssueFeedViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authHelper = AuthHelper.getInstance(applicationContext)

        lifecycleScope.launch(Dispatchers.Main) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                checkIfSubscriptionElapsed()
                maybeShowTryPdfDialog()
                maybeShowLoggedOutDialog()
            }
        }

        // create WebView then throw it away so later instantiations are faster
        // otherwise we have lags in the [CoverFlowFragment]
        WebView(this)
    }

    override fun onResume() {
        super.onResume()
        issueFeedViewModel.pdfModeLiveData.observeDistinctIgnoreFirst(this) {
            recreate()
        }
    }

    override fun onStop() {
        showLoggedOutDialog?.dismiss()
        tryPdfDialog?.dismiss()
        super.onStop()
    }

    private var showLoggedOutDialog: AlertDialog? = null
    private suspend fun maybeShowLoggedOutDialog() {
        if (issueFeedViewModel.getPdfMode() && !authHelper.isLoggedIn()) {
            showLoggedOutDialog = AlertDialog.Builder(this@MainActivity)
                .setMessage(R.string.pdf_mode_better_to_be_logged_in_hint)
                .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                .setNegativeButton(R.string.login_button) { dialog, _ ->
                    startActivityForResult(
                        Intent(this@MainActivity, LoginActivity::class.java),
                        ACTIVITY_LOGIN_REQUEST_CODE
                    )
                    dialog.dismiss()
                }
                .show()
        }
    }

    private var tryPdfDialog: AlertDialog? = null
    private suspend fun maybeShowTryPdfDialog() {
        val timesPdfShown = generalDataStore.tryPdfDialogCount.get()
        if (timesPdfShown < 1) {
            tryPdfDialog = AlertDialog.Builder(this)
                .setView(R.layout.dialog_try_pdf)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    CoroutineScope(Dispatchers.Main).launch {
                        generalDataStore.tryPdfDialogCount.set(timesPdfShown + 1)
                        dialog.dismiss()
                    }
                }
                .show()
            tryPdfDialog?.findViewById<ImageButton>(R.id.button_close)?.setOnClickListener {
                CoroutineScope(Dispatchers.Main).launch {
                    generalDataStore.tryPdfDialogCount.set(timesPdfShown + 1)
                    tryPdfDialog?.dismiss()
                }
            }
        }
    }

    private suspend fun checkIfSubscriptionElapsed() {
        val authStatus = authHelper.status.get()
        val isElapsedButWaiting = authHelper.elapsedButWaiting.get()
        if (authStatus == AuthStatus.elapsed && !isElapsedButWaiting) {
            showSubscriptionElapsedPopup()
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

    fun showSubscriptionElapsedPopup() {
        val popUpFragment = SubscriptionElapsedDialogFragment()
        popUpFragment.show(
            supportFragmentManager,
            "showSubscriptionElapsed"
        )
    }
}
