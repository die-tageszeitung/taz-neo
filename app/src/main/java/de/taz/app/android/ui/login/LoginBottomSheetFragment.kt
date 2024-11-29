package de.taz.app.android.ui.login

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialog
import de.taz.app.android.R
import de.taz.app.android.base.FullscreenViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentBottomSheetLoginBinding
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.SuccessfulLoginAction
import de.taz.app.android.ui.login.fragments.ConfirmEmailFragment
import de.taz.app.android.ui.login.fragments.CredentialsMissingFragment
import de.taz.app.android.ui.login.fragments.EmailAlreadyLinkedFragment
import de.taz.app.android.ui.login.fragments.LoginFragment
import de.taz.app.android.ui.login.fragments.NamesMissingFragment
import de.taz.app.android.ui.login.fragments.PasswordEmailSentFragment
import de.taz.app.android.ui.login.fragments.PasswordRequestFragment
import de.taz.app.android.ui.login.fragments.PasswordRequestNoMailFragment
import de.taz.app.android.ui.login.fragments.RegistrationSuccessfulFragment
import de.taz.app.android.ui.login.fragments.SubscriptionAlreadyLinkedFragment
import de.taz.app.android.ui.login.fragments.SubscriptionMissingFragment
import de.taz.app.android.ui.login.fragments.SubscriptionTakenFragment
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionAccountFragment
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionExtendPrintPlusDigiFragment
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionNameFragment
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionSwitchPrint2DigiFragment
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionTrialOnlyFragment
import de.taz.app.android.util.hideSoftInputKeyboard
import kotlinx.coroutines.launch

class LoginBottomSheetFragment : FullscreenViewBindingBottomSheetFragment<FragmentBottomSheetLoginBinding>() {
    companion object {
        const val TAG: String = "LoginBottomSheetFragment"
        private const val ARG_REQUEST_PASSWORD = "requestPassword"
        private const val ARG_ARTICLE_NAME = "articleName"

        fun newInstance(
            requestPassword: Boolean = false,
            articleName: String? = null,
        ) = LoginBottomSheetFragment().apply {
            arguments = bundleOf(
                ARG_REQUEST_PASSWORD to requestPassword,
                ARG_ARTICLE_NAME to articleName,
            )
        }

        /**
         * Show the [LoginBottomSheetFragment] with its default [TAG].
         * Does nothing if a fragment with [TAG] is already present in the fragmentManger.
         */
        fun showSingleInstance(fragmentManager: FragmentManager, articleName: String?) {
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                newInstance(articleName = articleName).showNow(fragmentManager, TAG)
            }
        }
    }


    private val viewModel by viewModels<LoginViewModel>()

    private lateinit var authHelper: AuthHelper
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    override fun onAttach(context: Context) {
        super.onAttach(context)

        authHelper = AuthHelper.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val requestPassword = arguments?.getBoolean(ARG_REQUEST_PASSWORD) ?: false
            if (requestPassword) {
                lifecycleScope.launch {
                    viewModel.apply {
                        username = authHelper.email.get()
                        status = LoginViewModelState.PASSWORD_REQUEST
                        backToSettingsAfterEmailSent = true
                    }
                }
            }

            viewModel.articleName = arguments?.getString(ARG_ARTICLE_NAME)
        }

        childFragmentManager.addOnBackStackChangedListener {
            // Whenever another Fragment is shown, we will hide the LoadingScreen
            hideLoadingScreen()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            (this as? BottomSheetDialog)?.apply {
                onBackPressedDispatcher.addCallback {
                    this@LoginBottomSheetFragment.handleOnBackPressed()
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initially the LoadingScreen should be hidden
        hideLoadingScreen()

        // Ensure that the viewModel is only initialized once by referencing it once before the coroutines are started
        // There seems to be an Android internal race condition when accessing the lazy viewModels from concurrent coroutines
        assert(viewModel != null)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.noInternetFlow.collect {
                        if (it) {
                            toastHelper.showNoConnectionToast()
                            viewModel.noInternet = false
                        }
                    }
                }

                launch {
                    viewModel.statusFlow.collect {
                        handleLoginViewModelState(it)
                    }
                }
            }
        }

        viewBinding.buttonClose.setOnClickListener {
            cancel()
        }
    }


    override fun onResume() {
        super.onResume()
        tracker.trackLoginScreen()
    }

    // Unfortunately hiding in onDismiss does not work reliable: the keyboard is re-opened on
    // the parent fragment/activity. Thus we override the dismiss() functions to call it early.
    // This mostly works - except when the BottomSheet is closed by dragging down.
    override fun dismiss() {
        hideSoftInputKeyboard()
        super.dismiss()
    }

    override fun dismissAllowingStateLoss() {
        hideSoftInputKeyboard()
        super.dismissAllowingStateLoss()
    }

    override fun dismissNow() {
        hideSoftInputKeyboard()
        super.dismissNow()
    }

    private fun handleLoginViewModelState(state: LoginViewModelState) {
        when (state) {
            LoginViewModelState.INITIAL -> showLoginForm()
            LoginViewModelState.LOADING -> showLoadingScreen()
            LoginViewModelState.LOGIN -> viewModel.login()
            LoginViewModelState.EMAIL_ALREADY_LINKED -> showEmailAlreadyLinked()
            LoginViewModelState.CREDENTIALS_INVALID -> showCredentialsInvalid()
            LoginViewModelState.CREDENTIALS_MISSING_LOGIN -> showMissingCredentials()
            LoginViewModelState.CREDENTIALS_MISSING_FAILED -> showMissingCredentials(failed = true)
            LoginViewModelState.CREDENTIALS_MISSING_REGISTER -> showMissingCredentials()
            LoginViewModelState.SUBSCRIPTION_ELAPSED -> done()
            LoginViewModelState.SUBSCRIPTION_INVALID -> showSubscriptionInvalid()
            LoginViewModelState.SUBSCRIPTION_MISSING -> showSubscriptionMissing()
            LoginViewModelState.SUBSCRIPTION_MISSING_INVALID_ID -> showSubscriptionMissing(invalidId = true)
            LoginViewModelState.SUBSCRIPTION_REQUEST -> showSubscriptionPossibilities()
            LoginViewModelState.SUBSCRIPTION_REQUEST_INVALID_EMAIL ->
                showSubscriptionAccount(mailInvalid = true)

            LoginViewModelState.SUBSCRIPTION_TAKEN -> showSubscriptionTaken()
            LoginViewModelState.PASSWORD_MISSING -> showLoginForm(passwordErrorId = R.string.login_password_error_empty)
            LoginViewModelState.PASSWORD_REQUEST -> showPasswordRequest()
            LoginViewModelState.PASSWORD_REQUEST_DONE -> showPasswordMailSent()
            LoginViewModelState.PASSWORD_REQUEST_INVALID_MAIL -> showPasswordRequest(invalidMail = true)
            LoginViewModelState.PASSWORD_REQUEST_NO_MAIL -> showPasswordRequestNoMail()
            LoginViewModelState.PASSWORD_REQUEST_INVALID_ID -> showPasswordRequest(
                showSubscriptionId = true,
                invalidId = true
            )

            LoginViewModelState.POLLING_FAILED -> {
                toastHelper.showToast(R.string.something_went_wrong_try_later)
                showLoginForm()
            }

            LoginViewModelState.REGISTRATION_EMAIL -> {
                lifecycleScope.launch {
                    authHelper.elapsedButWaiting.set(viewModel.isElapsed())
                    showConfirmEmail()
                }
            }

            LoginViewModelState.REGISTRATION_SUCCESSFUL -> showRegistrationSuccessful()
            LoginViewModelState.USERNAME_MISSING -> showLoginForm(usernameErrorId = R.string.login_username_error_empty)
            LoginViewModelState.DONE -> done()
            LoginViewModelState.NAME_MISSING -> showNamesMissing()
            LoginViewModelState.SUBSCRIPTION_NAME -> showSubscriptionName()
            LoginViewModelState.SUBSCRIPTION_ACCOUNT -> showSubscriptionAccount()
            LoginViewModelState.SUBSCRIPTION_ACCOUNT_INVALID ->
                showSubscriptionAccount(subscriptionInvalid = true)

            LoginViewModelState.PASSWORD_REQUEST_SUBSCRIPTION_ID ->
                showPasswordRequest(showSubscriptionId = true)

            LoginViewModelState.SUBSCRIPTION_ALREADY_LINKED -> showSubscriptionAlreadyLinked()
            LoginViewModelState.SWITCH_PRINT_2_DIGI_REQUEST -> showSwitchPrint2DigiForm()
            LoginViewModelState.EXTEND_PRINT_WITH_DIGI_REQUEST -> showExtendPrintWithDigiForm()
        }
    }

    private fun handleOnBackPressed() {
        hideSoftInputKeyboard()
        if (viewBinding.loadingScreen.root.isVisible) {
            hideLoadingScreen()
        } else if (childFragmentManager.backStackEntryCount == 2) {
            // FIXME (johannes): as we don't have a history within the LoginViewModel,
            //   we are relying on the FragmentManger backstack for going back.
            //   Unfortunately this will result in a wrong behavior when first back was used,
            //   and then second the Activity is switched. As the LoginBottomSheetFragment will
            //   restart the status Flow collector on STARTED, it will pick up the previous state
            //   from the the LoginViewModel and the according Fragment will be shown.
            //   A clean solution would add a backstack to the LoginViewModel, but for a quick fix
            //   we can simply reset to INITIAL if there is only the LoginFragment and exactly 1
            //   other Fragment on the backstack.
            //   Note: that this will still loose the additional info shown on the login form,
            //   with the states USERNAME_MISSING and PASSWORD_MISSING
            //   Another option would be to change the Flow collection restart to CREATED which
            //   would mitigate most of the visible problems (while the state would still be wrong)
            viewModel.status = LoginViewModelState.INITIAL
        } else if (childFragmentManager.backStackEntryCount > 2) {
            childFragmentManager.popBackStack()
        } else {
            cancel()
            // FIXME (johannes): how to inform callee about failure?
        }
    }

    // region helper functions
    private fun showFragment(fragment: Fragment) {
        val fragmentClassName = fragment::class.java.name

        childFragmentManager.apply {
            popBackStack(fragmentClassName, POP_BACK_STACK_INCLUSIVE)
            commit {
                replace(R.id.login_fragment_placeholder, fragment)
                addToBackStack(fragmentClassName)
            }
        }
    }

    private fun showLoadingScreen() {
        log.verbose("showLoadingScreen")
        viewBinding.loadingScreen.root.isVisible = true
    }

    private fun hideLoadingScreen() {
        log.verbose("hideLoadingScreen")
        viewBinding.loadingScreen.root.isVisible = false
    }
    // endregion


    // region methods called by LoginBaseFragments
    fun done() {
        log.verbose("done")
        val successfulLoginAction = (activity as? SuccessfulLoginAction)
        successfulLoginAction?.onLogInSuccessful(viewModel.articleName)
        dismiss()
    }

    fun back() = handleOnBackPressed()
    fun cancel() = dismiss()
    // endregion

    private fun showLoginForm(
        @StringRes usernameErrorId: Int? = null, @StringRes passwordErrorId: Int? = null
    ) {
        log.verbose("showLoginForm")
        showFragment(
            LoginFragment.create(
                usernameErrorId = usernameErrorId, passwordErrorId = passwordErrorId
            )
        )
    }

    private fun showConfirmEmail() {
        log.verbose("showConfirmEmail")
        showFragment(ConfirmEmailFragment())
    }

    private fun showEmailAlreadyLinked() {
        log.verbose("showEmailLinked")
        showFragment(EmailAlreadyLinkedFragment())
    }

    private fun showSubscriptionAlreadyLinked() {
        log.verbose("showSubscriptionAlreadyLinked")
        showFragment(SubscriptionAlreadyLinkedFragment())
    }

    private fun showSubscriptionMissing(invalidId: Boolean = false) {
        log.verbose("showSubscriptionMissing")
        viewModel.validCredentials = true
        showFragment(SubscriptionMissingFragment.create(invalidId))
    }

    private fun showSubscriptionTaken() {
        log.verbose("showSubscriptionTaken")
        showFragment(SubscriptionTakenFragment())
    }

    private fun showMissingCredentials(failed: Boolean = false) {
        log.verbose("showMissingCredentials - failed: $failed")
        // Pretty sure this is never being called. so we inform sentry now to see later on if this will be called:
        SentryWrapper.captureMessage("WE THOUGHT THIS NEVER HAPPENED: show CredentialsMissingFragment")
        showFragment(
            CredentialsMissingFragment.create(
                failed = failed
            )
        )
    }

    private fun showCredentialsInvalid() {
        log.verbose("showCredentialsInvalid")
        toastHelper.showToast(R.string.toast_login_failed_retry)
        if (authHelper.authInfoMessage != null) {
            showFragment(
                LoginFragment.create(
                    errorMessage = authHelper.authInfoMessage
                )
            )
        } else {
            showFragment(
                LoginFragment.create(
                    usernameErrorId = R.string.login_error_unknown_credentials
                )
            )
        }
    }

    private fun showSubscriptionInvalid() = showCredentialsInvalid()

    private fun showSubscriptionPossibilities() {
        log.verbose("showLoginRequestTestSubscription")
        viewModel.status = LoginViewModelState.LOADING
        lifecycleScope.launch {
            showFragment(
                SubscriptionTrialOnlyFragment.newInstance(
                    elapsed = authHelper.isElapsed()
                )
            )
        }
    }

    private fun showSwitchPrint2DigiForm() {
        log.verbose("showPrint2DigiForm")
        showFragment(
            SubscriptionSwitchPrint2DigiFragment()
        )
    }

    private fun showExtendPrintWithDigiForm() {
        log.verbose("showPrintPlusDigiForm")
        showFragment(
            SubscriptionExtendPrintPlusDigiFragment()
        )
    }

    private fun showRegistrationSuccessful() {
        log.verbose("showLoginRegistrationSuccessful")
        showFragment(RegistrationSuccessfulFragment())
    }

    private fun showPasswordRequest(
        showSubscriptionId: Boolean = false,
        invalidId: Boolean = false,
        invalidMail: Boolean = false
    ) {
        log.verbose("showPasswordRequest")
        showFragment(
            PasswordRequestFragment.create(
                invalidId = invalidId,
                invalidMail = invalidMail,
                showSubscriptionId = showSubscriptionId
            )
        )
    }

    private fun showPasswordMailSent() {
        log.verbose("showPasswordMailSent")
        showFragment(PasswordEmailSentFragment())
    }

    private fun showPasswordRequestNoMail() {
        log.verbose("showPasswordRequestNoMail")
        showFragment(PasswordRequestNoMailFragment())
    }

    private fun showNamesMissing() {
        log.verbose("showNamesMissing")
        // Pretty sure this is never being called. so we inform sentry now to see later on if this will be called:
        SentryWrapper.captureMessage("WE THOUGHT THIS NEVER HAPPENED: show NamesMissingFragment")
        showFragment(NamesMissingFragment())
    }

    private fun showSubscriptionName(
        nameTooLong: Boolean = false,
        firstNameEmpty: Boolean = false,
        firstNameInvalid: Boolean = false,
        surnameEmpty: Boolean = false,
        surnameInvalid: Boolean = false
    ) {
        log.verbose("showSubscriptionName")
        showFragment(
            SubscriptionNameFragment.newInstance(
                nameTooLong = nameTooLong,
                firstNameEmpty = firstNameEmpty,
                firstNameInvalid = firstNameInvalid,
                surnameEmpty = surnameEmpty,
                surnameInvalid = surnameInvalid,
            )
        )
    }

    private fun showSubscriptionAccount(
        mailInvalid: Boolean = false, subscriptionInvalid: Boolean = false
    ) {
        log.verbose("showSubscriptionAccount")
        showFragment(
            SubscriptionAccountFragment.newInstance(
                mailInvalid = mailInvalid, subscriptionInvalid = subscriptionInvalid
            )
        )
    }
}