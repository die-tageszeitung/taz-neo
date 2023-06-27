package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.databinding.FragmentArticleReadOnBinding
import de.taz.app.android.getTazApplication
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.SuccessfulLoginAction
import de.taz.app.android.ui.issueViewer.IssueViewerWrapperFragment
import de.taz.app.android.ui.login.LoginContract
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetViewModel.UIState.FormInvalidMessageLength
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetViewModel.UIState.Init
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetViewModel.UIState.Sent
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetViewModel.UIState.SubmissionError
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetViewModel.UIState.UnexpectedFailure
import de.taz.app.android.util.Log
import de.taz.app.android.util.hideSoftInputKeyboard
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArticleLoginFragment : ViewBindingFragment<FragmentArticleReadOnBinding>(),
    ActivityResultCallback<LoginContract.Output> {

    private val log by Log

    private var articleFileName: String? = null
    private val elapsedViewModel by viewModels<SubscriptionElapsedBottomSheetViewModel>()
    private lateinit var activityResultLauncher: ActivityResultLauncher<LoginContract.Input>
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    companion object {
        fun create(articleFileName: String): ArticleLoginFragment {
            val fragment = ArticleLoginFragment()
            fragment.articleFileName = articleFileName
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityResultLauncher = registerForActivityResult(LoginContract(), this)
        toastHelper = ToastHelper.getInstance(requireActivity().applicationContext)
        tracker = getTazApplication().tracker
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupInteractionHandlers()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                elapsedViewModel.isElapsedFlow.collect { isElapsed ->
                    if (isElapsed) {
                        startElapsedHandling()
                    } else {
                        stopElapsedHandling()
                        showLoginSubscribeUi()
                    }
                }
            }
        }
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

            // Elapsed form send button
            sendButton.setOnClickListener {
                // FIXME (johannes): Add tracking events for the integrated elapsed form
                onSubmitElapsedForm()
            }
            
            // Elapsed form cancel button
            cancelButton.setOnClickListener {
                // FIXME (johannes): Add tracking events for the integrated elapsed form
                hideAllViews()
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

    private fun onSubmitElapsedForm() {
        viewBinding.apply {
            elapsedViewModel.sendMessage(
                messageToSubscriptionService.editText?.text.toString(),
                letTheSubscriptionServiceContactYouCheckbox.isChecked
            )
        }
    }

    private var elapsedFlowJob: Job? = null
    private fun startElapsedHandling() {
        elapsedFlowJob?.cancel()
        elapsedFlowJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main.immediate) {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                elapsedViewModel.uiStateFlow.collect {
                    when (it) {
                        Init -> showElapsedUi()
                        FormInvalidMessageLength -> showMessageLengthErrorHint()
                        UnexpectedFailure -> handleUnexpectedElapsedFormSendFailure()
                        Sent -> handleElapsedFormSend()
                        is SubmissionError -> handleElapsedFormSubmissionError(it.message)
                    }
                }
            }
        }
    }

    private fun stopElapsedHandling() {
        elapsedFlowJob?.cancel()
    }

    private fun handleUnexpectedElapsedFormSendFailure() {
        toastHelper.showToast(R.string.something_went_wrong_try_later)
        elapsedViewModel.errorWasHandled()
    }

    private fun handleElapsedFormSend() {
        stopElapsedHandling()
        toastHelper.showToast(R.string.subscription_inquiry_send_success_toast, long = true)
        hideAllViews()
    }

    private fun handleElapsedFormSubmissionError(message: String) {
        val toastMessage = getString(R.string.subscription_inquiry_submission_error, message)
        toastHelper.showToast(toastMessage, long = true)
        elapsedViewModel.errorWasHandled()
    }

    private fun showLoginSubscribeUi() {
        viewBinding.apply {
            readOnLoginGroup.visibility = View.VISIBLE
            readOnElapsedGroup.visibility = View.GONE

            if (BuildConfig.IS_LMD) {
                readOnSeparatorLine.visibility = View.GONE
                readOnTrialSubscriptionBox.visibility = View.GONE
                readOnSwitchPrint2digiBox.visibility = View.GONE
                readOnExtendPrintWithDigiBox.visibility = View.GONE
            }
            else {
                readOnSeparatorLine.visibility = View.VISIBLE
                readOnTrialSubscriptionBox.visibility = View.VISIBLE
                readOnSwitchPrint2digiBox.visibility = View.VISIBLE
                readOnExtendPrintWithDigiBox.visibility = View.VISIBLE
            }
        }
    }

    private fun showMessageLengthErrorHint() {
        viewBinding.apply {
            messageToSubscriptionService.error = getString(R.string.popup_login_elapsed_message_to_short)
        }
    }

    private suspend fun showElapsedUi() = withContext(Dispatchers.Main) {
        viewBinding.apply {
            readOnLoginGroup.visibility = View.GONE
            readOnSeparatorLine.visibility = View.GONE
            readOnTrialSubscriptionBox.visibility = View.GONE
            readOnSwitchPrint2digiBox.visibility = View.GONE
            readOnExtendPrintWithDigiBox.visibility = View.GONE
            readOnElapsedGroup.visibility = View.VISIBLE

            readOnElapsedTitle.text = elapsedViewModel.elapsedTitleStringFlow.first()
            readOnElapsedDescription.text = elapsedViewModel.elapsedDescriptionStringFlow.first()
        }
    }

    private fun hideAllViews() {
        viewBinding.apply {
            readOnLoginGroup.visibility = View.GONE
            readOnSeparatorLine.visibility = View.GONE
            readOnTrialSubscriptionBox.visibility = View.GONE
            readOnSwitchPrint2digiBox.visibility = View.GONE
            readOnExtendPrintWithDigiBox.visibility = View.GONE
            readOnElapsedGroup.visibility = View.GONE
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
}