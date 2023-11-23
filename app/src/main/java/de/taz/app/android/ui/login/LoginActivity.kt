package de.taz.app.android.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityLoginBinding
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.login.fragments.*
import de.taz.app.android.ui.login.fragments.subscription.*
import de.taz.app.android.ui.main.MAIN_EXTRA_ARTICLE
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setBottomNavigationBackActivity
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val ACTIVITY_LOGIN_REQUEST_CODE: Int = 161
const val LOGIN_EXTRA_USERNAME: String = "LOGIN_EXTRA_USERNAME"
const val LOGIN_EXTRA_PASSWORD: String = "LOGIN_EXTRA_PASSWORD"
const val LOGIN_EXTRA_OPTION: String = "LOGIN_EXTRA_OPTION"
const val LOGIN_EXTRA_ARTICLE = "LOGIN_EXTRA_ARTICLE"
const val LOGIN_EXTRA_FROM_HOME = "LOGIN_EXTRA_FROM_HOME"

/**
 * Activity used to register or login a user
 *
 * This activity can be called via [LoginContract] with [LoginContract.Input] to login or register a
 * user from another part of the app
 * e.g. [de.taz.app.android.ui.login.fragments.ArticleLoginFragment]
 *
 */
class LoginActivity : ViewBindingActivity<ActivityLoginBinding>() {

    private val log by Log

    private val viewModel by viewModels<LoginViewModel>()

    private var article: String? = null

    private lateinit var authHelper: AuthHelper
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker

    private val loadingScreen by lazy { viewBinding.loadingScreen.root }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authHelper = AuthHelper.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)
        tracker = Tracker.getInstance(applicationContext)

        // The AudioPlayer shall stop when a users logs in (or registers)
        AudioPlayerService.getInstance(applicationContext).apply {
            dismissPlayer()
        }
        article = intent.getStringExtra(LOGIN_EXTRA_ARTICLE)
        viewModel.backToHome = intent.getBooleanExtra(LOGIN_EXTRA_FROM_HOME, false)

        viewBinding.navigationBottom.apply {
            setOnItemSelectedListener {
                this@LoginActivity.apply {
                    val data = Intent()
                    setResult(Activity.RESULT_CANCELED, data)
                    finish()
                }
                true
            }
        }

        val option: LoginContract.Option? =
            intent.getStringExtra(LOGIN_EXTRA_OPTION)?.let { LoginContract.Option.valueOf(it) }

        val initialStatus: LoginViewModelState =
            when(option) {
                LoginContract.Option.LOGIN -> LoginViewModelState.LOGIN
                LoginContract.Option.REGISTER -> LoginViewModelState.SUBSCRIPTION_REQUEST
                LoginContract.Option.FORGOT_PASSWORD -> LoginViewModelState.PASSWORD_REQUEST
                LoginContract.Option.REQUEST_PASSWORD_RESET -> LoginViewModelState.PASSWORD_REQUEST
                LoginContract.Option.PRINT_TO_DIGI -> LoginViewModelState.SWITCH_PRINT_2_DIGI_REQUEST
                LoginContract.Option.EXTEND_PRINT -> LoginViewModelState.EXTEND_PRINT_WITH_DIGI_REQUEST
                null -> LoginViewModelState.INITIAL
            }
        if (option == LoginContract.Option.REQUEST_PASSWORD_RESET) {
            viewModel.backToSettingsAfterEmailSent = true
        }

        viewModel.apply {
            username = intent.getStringExtra(LOGIN_EXTRA_USERNAME)
            password = intent.getStringExtra(LOGIN_EXTRA_PASSWORD)
            status.postValue(initialStatus)
        }

        viewModel.backToArticle = article != null

        viewModel.status.observe(this) { loginViewModelState: LoginViewModelState? ->
            if (loginViewModelState != LoginViewModelState.LOADING)
                hideLoadingScreen()

            when (loginViewModelState) {
                LoginViewModelState.INITIAL -> {
                    showLoginForm()
                }
                LoginViewModelState.LOADING -> {
                    showLoadingScreen()
                }
                LoginViewModelState.LOGIN -> {
                    viewModel.login()
                }
                LoginViewModelState.EMAIL_ALREADY_LINKED -> {
                    showEmailAlreadyLinked()
                }
                LoginViewModelState.CREDENTIALS_INVALID -> {
                    showCredentialsInvalid()
                }
                LoginViewModelState.CREDENTIALS_MISSING_LOGIN -> {
                    showMissingCredentials()
                }
                LoginViewModelState.CREDENTIALS_MISSING_FAILED -> {
                    showMissingCredentials(failed = true)
                }
                LoginViewModelState.CREDENTIALS_MISSING_REGISTER -> {
                    showMissingCredentials()
                }
                LoginViewModelState.SUBSCRIPTION_ELAPSED -> {
                    done()
                }
                LoginViewModelState.SUBSCRIPTION_INVALID -> {
                    showSubscriptionInvalid()
                }
                LoginViewModelState.SUBSCRIPTION_MISSING -> {
                    showSubscriptionMissing()
                }
                LoginViewModelState.SUBSCRIPTION_MISSING_INVALID_ID -> {
                    showSubscriptionMissing(invalidId = true)
                }
                LoginViewModelState.SUBSCRIPTION_REQUEST -> {
                    showSubscriptionPossibilities()
                }
                LoginViewModelState.SUBSCRIPTION_REQUEST_INVALID_EMAIL -> {
                    showSubscriptionAccount(mailInvalid = true)
                }
                LoginViewModelState.SUBSCRIPTION_TAKEN -> {
                    showSubscriptionTaken()
                }
                LoginViewModelState.PASSWORD_MISSING -> {
                    showLoginForm(passwordErrorId = R.string.login_password_error_empty)
                }
                LoginViewModelState.PASSWORD_REQUEST -> {
                    showPasswordRequest()
                }
                LoginViewModelState.PASSWORD_REQUEST_DONE -> {
                    showPasswordMailSent()
                }
                LoginViewModelState.PASSWORD_REQUEST_INVALID_MAIL -> {
                    showPasswordRequest(invalidMail = true)
                }
                LoginViewModelState.PASSWORD_REQUEST_NO_MAIL -> {
                    showPasswordRequestNoMail()
                }
                LoginViewModelState.PASSWORD_REQUEST_INVALID_ID -> {
                    showPasswordRequest(showSubscriptionId = true, invalidId = true)
                }
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
                LoginViewModelState.SUBSCRIPTION_ACCOUNT_MAIL_INVALID -> showSubscriptionAccount(
                    mailInvalid = true
                )
                LoginViewModelState.SUBSCRIPTION_NAME_FIRST_NAME_EMPTY -> showSubscriptionName(
                    firstNameEmpty = true
                )
                LoginViewModelState.SUBSCRIPTION_NAME_FIRST_NAME_INVALID -> showSubscriptionName(
                    firstNameInvalid = true
                )
                LoginViewModelState.SUBSCRIPTION_NAME_SURNAME_EMPTY -> showSubscriptionName(
                    surnameEmpty = true
                )
                LoginViewModelState.SUBSCRIPTION_NAME_SURNAME_INVALID -> showSubscriptionName(
                    surnameInvalid = true
                )
                // TODO(peter) Check if this might can be removed totally since it's not possible to reach
                //  since we don't let the user send this data.
                LoginViewModelState.SUBSCRIPTION_BANK_ACCOUNT_HOLDER_INVALID -> showLoginForm()
                LoginViewModelState.SUBSCRIPTION_BANK_IBAN_EMPTY -> showLoginForm()
                LoginViewModelState.SUBSCRIPTION_BANK_IBAN_INVALID -> showLoginForm()
                LoginViewModelState.SUBSCRIPTION_BANK_IBAN_NO_SEPA -> showLoginForm()
                LoginViewModelState.SUBSCRIPTION_PRICE_INVALID -> showLoginForm()

                LoginViewModelState.SUBSCRIPTION_NAME_NAME_TOO_LONG -> showSubscriptionName(
                    nameTooLong = true
                )
                LoginViewModelState.SUBSCRIPTION_ACCOUNT_INVALID -> {
                    showSubscriptionAccount(subscriptionInvalid = true)
                }
                // TODO(peter) Check if this might can be removed totally since it's not possible to reach
                //  since we don't let the user send this data.
                LoginViewModelState.SUBSCRIPTION_ADDRESS_CITY_INVALID -> showLoginForm()
                LoginViewModelState.SUBSCRIPTION_ADDRESS_COUNTRY_INVALID -> showLoginForm()
                LoginViewModelState.SUBSCRIPTION_ADDRESS_STREET_INVALID -> showLoginForm()
                LoginViewModelState.SUBSCRIPTION_ADDRESS_POSTCODE_INVALID -> showLoginForm()
                LoginViewModelState.PASSWORD_REQUEST_SUBSCRIPTION_ID -> {
                    showPasswordRequest(showSubscriptionId = true)
                }
                LoginViewModelState.SUBSCRIPTION_ALREADY_LINKED -> {
                    showSubscriptionAlreadyLinked()
                }
                LoginViewModelState.SWITCH_PRINT_2_DIGI_REQUEST -> {
                    showSwitchPrint2DigiForm()
                }
                LoginViewModelState.EXTEND_PRINT_WITH_DIGI_REQUEST -> {
                    showExtendPrintWithDigiForm()
                }
                null -> {
                    Sentry.captureMessage("login status is null")
                    viewModel.status.postValue(LoginViewModelState.INITIAL)
                }
            }
        }

        viewModel.noInternet.distinctUntilChanged().observe(this) {
            if (it) {
                toastHelper.showNoConnectionToast()
            }
        }

    }

    override fun onResume() {
        super.onResume()
        setBottomNavigationBackActivity(this, BottomNavigationItem.Settings)
        setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.ChildOf(BottomNavigationItem.Settings)
        )
        tracker.trackLoginScreen()
    }

    override fun onDestroy() {
        setBottomNavigationBackActivity(null, BottomNavigationItem.Settings)
        super.onDestroy()
    }

    private fun showLoginForm(
        @StringRes usernameErrorId: Int? = null,
        @StringRes passwordErrorId: Int? = null
    ) {
        log.verbose("showLoginForm")
        showFragment(
            LoginFragment.create(
                usernameErrorId = usernameErrorId,
                passwordErrorId = passwordErrorId
            )
        )
    }

    private fun hideLoadingScreen() = runOnUiThread {
        log.verbose("hideLoadingScreen")
        loadingScreen.visibility = View.GONE
    }

    private fun showLoadingScreen() = runOnUiThread {
        log.verbose("showLoadingScreen")
        loadingScreen.visibility = View.VISIBLE
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
        showFragment(
            CredentialsMissingFragment.create(
                failed = failed
            )
        )
    }

    private fun showCredentialsInvalid() {
        log.verbose("showCredentialsInvalid")
        toastHelper.showToast(R.string.login_error_unknown_credentials)
        showFragment(
            LoginFragment.create(
                usernameErrorId = R.string.login_error_unknown_credentials
            )
        )
    }

    private fun showSubscriptionInvalid() = showCredentialsInvalid()

    private fun showSubscriptionPossibilities() {
        log.verbose("showLoginRequestTestSubscription")
        viewModel.status.postValue(LoginViewModelState.LOADING)
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
        showFragment(NamesMissingFragment())
    }

    fun done() {
        log.verbose("done")
        showLoadingScreen()
        lifecycleScope.launch(Dispatchers.Main) {
            val data = Intent()
            if (authHelper.isValid()) {
                article = article?.replace("public.", "")

                article?.let {
                    data.putExtra(MAIN_EXTRA_ARTICLE, article)
                }
            }
            setResult(Activity.RESULT_OK, data)
            finish()
        }
    }

    private fun showFragment(fragment: Fragment) {
        val fragmentClassName = fragment::class.java.name

        supportFragmentManager.popBackStack(fragmentClassName, POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.activity_login_fragment_placeholder, fragment)
            addToBackStack(fragmentClassName)
            commit()
        }

        fragment.lifecycleScope.launchWhenResumed {
            hideLoadingScreen()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (loadingScreen.visibility == View.VISIBLE) {
            hideLoadingScreen()
        } else {
            if (supportFragmentManager.backStackEntryCount == 1) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            } else {
                setBottomNavigationBackActivity(null, BottomNavigationItem.Settings)
                super.onBackPressed()
            }
        }
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
        mailInvalid: Boolean = false,
        subscriptionInvalid: Boolean = false
    ) {
        log.verbose("showSubscriptionAccount")
        showFragment(
            SubscriptionAccountFragment.newInstance(
                mailInvalid = mailInvalid,
                subscriptionInvalid = subscriptionInvalid
            )
        )
    }
}