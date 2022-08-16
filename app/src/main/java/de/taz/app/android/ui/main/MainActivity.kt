package de.taz.app.android.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.TazApplication
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.dto.SubscriptionFormDataType
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.ActivityMainBinding
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.login.LOGIN_EXTRA_REGISTER
import de.taz.app.android.ui.login.LoginActivity
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
    private suspend fun maybeShowLoggedOutDialog() {
        if (generalDataStore.pdfMode.get() && !authHelper.isValid()) {
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

            loggedOutDialog!!.show()

        }
    }

    private var tryPdfDialog: AlertDialog? = null
    private suspend fun maybeShowTryPdfDialog() {
        val timesPdfShown = generalDataStore.tryPdfDialogCount.get()
        if (timesPdfShown < 1) {
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
    }

    private suspend fun checkIfSubscriptionElapsed() {
        val authStatus = authHelper.status.get()
        val isElapsedButWaiting = authHelper.elapsedButWaiting.get()
        val elapsedOn = DateHelper.stringToLongLocalizedString (authHelper.message.get())
        val alreadyShown = (application as TazApplication).elapsedPopupAlreadyShown
        if (authStatus == AuthStatus.elapsed && !isElapsedButWaiting && !alreadyShown) {
    //        val customerInfo = ApiService.getInstance(applicationContext).customerInfo

            if (BuildConfig.IS_NON_FREE) showSubscriptionElapsedDialogNonFree(elapsedOn)
            else showSubscriptionElapsedDialog(elapsedOn)
            (application as TazApplication).elapsedPopupAlreadyShown = true
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

    private fun showSubscriptionElapsedDialogNonFree(elapsedOn: String?) {
        val dialogView = layoutInflater.inflate(R.layout.fragment_subscription_elapsed_dialog, null)
        val dialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setView(dialogView)
            .setTitle(getString(R.string.popup_login_elapsed_header, elapsedOn))
            .setMessage(R.string.popup_login_elapsed_text)
            .setPositiveButton(R.string.popup_login_elapsed_positive_button) { dialog, _ ->
                dialog.dismiss()
                val message =
                    dialogView.findViewById<EditText>(R.id.message_to_subscription_service)?.text.toString()
                val isChecked =
                    dialogView.findViewById<CheckBox>(R.id.let_the_subscription_service_contact_you_checkbox)?.isChecked
                Log.d("111", "message: $message   isChecked? $isChecked")
                applicationScope.launch {
                    ApiService.getInstance(applicationContext).subscriptionFormData(
                        type = SubscriptionFormDataType.trialSubscription,
                        message = message,
                        requestCurrentSubscriptionOpportunities = isChecked
                    )
                }
            }
            .setNegativeButton(R.string.cancel_button) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        //TODO(eike): make the mailto-link in the message clickable
        dialog.show()
    }

    private fun showSubscriptionElapsedDialog(elapsedOn: String?) {
        val dialog = MaterialAlertDialogBuilder(this@MainActivity)
            .setTitle(getString(R.string.popup_login_elapsed_header, elapsedOn))
            .setMessage(R.string.popup_login_elapsed_text)
            .setPositiveButton(R.string.order_button) { dialog, _ ->
                startActivityForResult(Intent(this@MainActivity, LoginActivity::class.java).apply {
                    putExtra(LOGIN_EXTRA_REGISTER, true)
                }, ACTIVITY_LOGIN_REQUEST_CODE)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel_button) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        //TODO(eike): make the mailto-link in the message clickable
        dialog.show()
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
