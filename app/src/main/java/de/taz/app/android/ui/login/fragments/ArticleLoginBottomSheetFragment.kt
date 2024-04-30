package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_HTML_FILE_DATA_POLICY
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentArticleLoginBottomSheetBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.monkey.setBehaviorState
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.SuccessfulLoginAction
import de.taz.app.android.ui.WebViewActivity
import de.taz.app.android.ui.issueViewer.IssueViewerWrapperFragment
import de.taz.app.android.ui.login.LoginContract
import de.taz.app.android.util.Log
import de.taz.app.android.util.hideSoftInputKeyboard
import io.sentry.Sentry

class ArticleLoginBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentArticleLoginBottomSheetBinding>(),
    ActivityResultCallback<LoginContract.Output> {

    private val log by Log

    private var articleFileName: String? = null
    private val activityResultLauncher: ActivityResultLauncher<LoginContract.Input> =
        registerForActivityResult(LoginContract(), this)
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    companion object {
        const val TAG = "articleLoginBottomSheetFragment"
        fun newInstance(articleFileName: String): ArticleLoginBottomSheetFragment {
            val articleLoginBottomSheetFragment = ArticleLoginBottomSheetFragment()
            articleLoginBottomSheetFragment.articleFileName = articleFileName
            return articleLoginBottomSheetFragment
        }

        /**
         * Show the [ArticleLoginBottomSheetFragment] with its default [TAG].
         * Does nothing if a fragment with [TAG] is already present in the fragmentManger.
         */
        fun showSingleInstance(fragmentManager: FragmentManager, articleFileName: String) {
            if (fragmentManager.findFragmentByTag(SubscriptionElapsedBottomSheetFragment.TAG) == null) {
                newInstance(articleFileName)
                    .show(fragmentManager, SubscriptionElapsedBottomSheetFragment.TAG)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val applicationContext = requireContext().applicationContext
        toastHelper = ToastHelper.getInstance(applicationContext)
        tracker = Tracker.getInstance(applicationContext)
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setBehaviorState(BottomSheetBehavior.STATE_EXPANDED)
        setupInteractionHandlers()
        showLoginSubscribeUi()
    }

    private fun setupInteractionHandlers() {
        viewBinding.apply {
            // Set listeners on all buttons - even if they are not not shown in case of elapsed
            readOnLoginButton.setOnClickListener {
                login()
            }

            readOnPassword.setOnEditorActionListener(
                OnEditorActionDoneListener{
                    login()
                }
            )

            readOnTrialSubscriptionBoxButton.setOnClickListener {
                register()
            }

            readOnSwitchPrint2digiBoxButton.setOnClickListener {
                switchPrintToDigi()
            }

            readOnExtendPrintWithDigiBoxButton.setOnClickListener {
                extendPrintWithDigi()
            }

            buttonClose.setOnClickListener {
                dismiss()
            }

            cancelButtonBottom.setOnClickListener {
                dismiss()
            }

            if (BuildConfig.IS_LMD){
                forgotPassword.visibility = View.GONE
            } else {
                forgotPassword.setOnClickListener {
                    forgotPassword()
                }
            }

            showHelp.setOnClickListener {
                showHelpDialog()
            }

            showDataPolicy.setOnClickListener {
                showDataPolicy()
            }
        }
    }

    private fun getUsername(): String = viewBinding.readOnUsername.text.toString().trim().lowercase()
    private fun getPassword(): String = viewBinding.readOnPassword.text.toString()

    private fun login() = startLoginActivity(LoginContract.Option.LOGIN)
    private fun register() = startLoginActivity(LoginContract.Option.REGISTER)
    private fun forgotPassword() = startLoginActivity(LoginContract.Option.FORGOT_PASSWORD)
    private fun switchPrintToDigi() = startLoginActivity(LoginContract.Option.PRINT_TO_DIGI)
    private fun extendPrintWithDigi() = startLoginActivity(LoginContract.Option.EXTEND_PRINT)

    private fun startLoginActivity(option: LoginContract.Option) {
        activityResultLauncher.launch(
            LoginContract.Input(
                option = option,
                username = getUsername(),
                password = getPassword(),
                articleFileName = articleFileName,
            )
        )
        hideSoftInputKeyboard()
    }

    /**
     * After a login we replace the [IssueViewerWrapperFragment] with a new instance of the
     * regular issue
     */
    override fun onActivityResult(result: LoginContract.Output) {
        if (result.success && result.articleFileName != null) {
            val successfulLoginActionActivity = activity as? SuccessfulLoginAction
            if (successfulLoginActionActivity != null) {
                successfulLoginActionActivity.onLogInSuccessful(result.articleFileName)
            }
            else {
                var hint = "Expected this activity to be SuccessfulLoginAction."
                if (activity != null) {
                    hint += " But it is ${activity!!::class.java.name}"
                }
                log.warn(hint)
                Sentry.captureMessage(hint)
            }
        }
    }


    private fun showLoginSubscribeUi() {
        viewBinding.apply {
            readOnLoginGroup.visibility = View.VISIBLE

            if (BuildConfig.IS_LMD) {
                readOnSeparatorLine.visibility = View.GONE
                readOnTrialSubscriptionBox.visibility = View.GONE
                readOnSwitchPrint2digiBox.visibility = View.GONE
                readOnExtendPrintWithDigiBox.visibility = View.GONE
                readOnCancelButton.visibility = View.GONE
            }
            else {
                readOnSeparatorLine.visibility = View.VISIBLE
                readOnTrialSubscriptionBox.visibility = View.VISIBLE
                readOnSwitchPrint2digiBox.visibility = View.VISIBLE
                readOnExtendPrintWithDigiBox.visibility = View.VISIBLE
                readOnCancelButton.visibility = View.VISIBLE
            }
        }
    }

    private fun showHelpDialog() {
        context?.let {
            val dialog = MaterialAlertDialogBuilder(it)
                .setMessage(R.string.fragment_login_help)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            dialog.show()
            tracker.trackLoginHelpDialog()
        }
    }

    private fun showDataPolicy() {
        activity?.apply {
            val intent = WebViewActivity.newIntent(this, WEBVIEW_HTML_FILE_DATA_POLICY)
            startActivity(intent)
        }
    }
}