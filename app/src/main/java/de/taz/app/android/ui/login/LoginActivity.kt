package de.taz.app.android.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.PriceInfo
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityLoginBinding
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
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
const val LOGIN_EXTRA_STATUS: String = "LOGIN_EXTRA_STATUS"
const val LOGIN_EXTRA_ARTICLE = "LOGIN_EXTRA_ARTICLE"

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

    private val loadingScreen by lazy { viewBinding.loadingScreen.root }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authHelper = AuthHelper.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)

        rootViewGroup?.moveContentBeneathStatusBar()

        article = intent.getStringExtra(LOGIN_EXTRA_ARTICLE)

        viewBinding.navigationBottom.apply {
            itemIconTintList = null

            // hack to not auto select first item
            menu.getItem(0).isCheckable = false

            setOnItemSelectedListener {
                this@LoginActivity.apply {
                    val data = Intent()
                    setResult(Activity.RESULT_CANCELED, data)
                    finish()
                }
                true
            }
        }

        val intentStatus: LoginViewModelState =
            intent.getStringExtra(LOGIN_EXTRA_STATUS)?.let { LoginViewModelState.valueOf(it) }
                ?: LoginViewModelState.INITIAL

        viewModel.apply {
            username = intent.getStringExtra(LOGIN_EXTRA_USERNAME)
            password = intent.getStringExtra(LOGIN_EXTRA_PASSWORD)
            status.postValue(intentStatus)
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
                    showPasswordRequest(invalidId = true)
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
                LoginViewModelState.SUBSCRIPTION_ADDRESS -> showSubscriptionAddress()
                LoginViewModelState.SUBSCRIPTION_ACCOUNT -> showSubscriptionAccount()
                LoginViewModelState.SUBSCRIPTION_BANK -> showSubscriptionBank()
                LoginViewModelState.SUBSCRIPTION_ACCOUNT_MAIL_INVALID -> showSubscriptionAccount(
                    mailInvalid = true
                )
                LoginViewModelState.SUBSCRIPTION_ADDRESS_FIRST_NAME_EMPTY -> showSubscriptionAddress(
                    firstNameEmpty = true
                )
                LoginViewModelState.SUBSCRIPTION_ADDRESS_FIRST_NAME_INVALID -> showSubscriptionAddress(
                    firstNameInvalid = true
                )
                LoginViewModelState.SUBSCRIPTION_ADDRESS_SURNAME_EMPTY -> showSubscriptionAddress(
                    surnameEmpty = true
                )
                LoginViewModelState.SUBSCRIPTION_ADDRESS_SURNAME_INVALID -> showSubscriptionAddress(
                    surnameInvalid = true
                )
                LoginViewModelState.SUBSCRIPTION_BANK_ACCOUNT_HOLDER_INVALID -> showSubscriptionBank(
                    accountHolderInvalid = true
                )
                LoginViewModelState.SUBSCRIPTION_BANK_IBAN_EMPTY -> showSubscriptionBank(
                    ibanEmpty = true
                )
                LoginViewModelState.SUBSCRIPTION_BANK_IBAN_INVALID -> showSubscriptionBank(
                    ibanInvalid = true
                )
                LoginViewModelState.SUBSCRIPTION_BANK_IBAN_NO_SEPA -> showSubscriptionBank(
                    ibanNoSepa = true
                )
                LoginViewModelState.SUBSCRIPTION_PRICE_INVALID -> showSubscriptionPossibilities(
                    priceInvalid = true
                )
                null -> {
                    Sentry.captureMessage("login status is null")
                    viewModel.status.postValue(LoginViewModelState.INITIAL)
                }
                LoginViewModelState.SUBSCRIPTION_ADDRESS_NAME_TOO_LONG -> showSubscriptionAddress(
                    nameTooLong = true
                )
                LoginViewModelState.SUBSCRIPTION_ACCOUNT_INVALID -> {
                    showSubscriptionAccount(subscriptionInvalid = true)
                }
                LoginViewModelState.SUBSCRIPTION_ADDRESS_CITY_INVALID -> {
                    showSubscriptionAddress(cityInvalid = true)
                }
                LoginViewModelState.SUBSCRIPTION_ADDRESS_COUNTRY_INVALID -> {
                    showSubscriptionAddress(countryInvalid = true)
                }
                LoginViewModelState.SUBSCRIPTION_ADDRESS_STREET_INVALID -> {
                    showSubscriptionAddress(streetInvalid = true)
                }
                LoginViewModelState.SUBSCRIPTION_ADDRESS_POSTCODE_INVALID -> {
                    showSubscriptionAddress(postcodeInvalid = true)
                }
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
            }
        }

        viewModel.noInternet.observeDistinct(this) {
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
    }

    override fun onDestroy() {
        setBottomNavigationBackActivity(null, BottomNavigationItem.Settings)
        super.onDestroy()
    }

    private fun showLoginForm(
        @StringRes usernameErrorId: Int? = null,
        @StringRes passwordErrorId: Int? = null
    ) {
        log.debug("showLoginForm")
        showFragment(
            LoginFragment.create(
                usernameErrorId = usernameErrorId,
                passwordErrorId = passwordErrorId
            )
        )
    }

    private fun hideLoadingScreen() = runOnUiThread {
        log.debug("hideLoadingScreen")
        loadingScreen.visibility = View.GONE
    }

    private fun showLoadingScreen() = runOnUiThread {
        log.debug("showLoadingScreen")
        loadingScreen.visibility = View.VISIBLE
    }

    private fun showConfirmEmail() {
        log.debug("showConfirmEmail")
        showFragment(ConfirmEmailFragment())
    }

    private fun showEmailAlreadyLinked() {
        log.debug("showEmailLinked")
        showFragment(EmailAlreadyLinkedFragment())
    }

    private fun showSubscriptionAlreadyLinked() {
        log.debug("showSubscriptionAlreadyLinked")
        showFragment(SubscriptionAlreadyLinkedFragment())
    }

    private fun showSubscriptionMissing(invalidId: Boolean = false) {
        log.debug("showSubscriptionMissing")
        viewModel.validCredentials = true
        showFragment(SubscriptionMissingFragment.create(invalidId))
    }

    private fun showSubscriptionTaken() {
        log.debug("showSubscriptionTaken")
        showFragment(SubscriptionTakenFragment())
    }

    private fun showMissingCredentials(failed: Boolean = false) {
        log.debug("showMissingCredentials - failed: $failed")
        showFragment(
            CredentialsMissingFragment.create(
                failed = failed
            )
        )
    }

    private fun showCredentialsInvalid() {
        log.debug("showCredentialsInvalid")
        toastHelper.showToast(R.string.login_error_unknown_credentials)
        showFragment(
            LoginFragment.create(
                usernameErrorId = R.string.login_error_unknown_credentials
            )
        )
    }

    private fun showSubscriptionInvalid() = showCredentialsInvalid()

    private fun showSubscriptionPossibilities(priceInvalid: Boolean = false) {
        log.debug("showLoginRequestTestSubscription")
        viewModel.status.postValue(LoginViewModelState.LOADING)
        lifecycleScope.launch {
            // if on non free flavor it is not allowed to buy stuff from the app,
            // so we show a fragment where we only allow the trial subscription:
            if (BuildConfig.IS_NON_FREE) {
                showFragment(
                    SubscriptionTrialOnlyFragment.createInstance(
                        elapsed = authHelper.isElapsed()
                    )
                )
            }
            // otherwise - on free flavor - we can call getPriceList to receive
            // current price list from api
            else {
                getPriceList()?.let {
                    showFragment(
                        SubscriptionPriceFragment.createInstance(
                            it,
                            invalidPrice = priceInvalid
                        )
                    )
                } ?: hideLoadingScreen()
            }
        }
    }

    private fun showSwitchPrint2DigiForm() {
        log.debug("showPrint2DigiForm")
        showFragment(
            SubscriptionSwitchPrint2DigiFragment()
        )
    }

    private fun showExtendPrintWithDigiForm() {
        log.debug("showPrintPlusDigiForm")
        showFragment(
            SubscriptionExtendPrintPlusDigiFragment()
        )
    }

    /**
     * get priceList synchronous to give user feedback
     */
    private suspend fun getPriceList(): List<PriceInfo>? {
        return try {
            ApiService.getInstance(applicationContext).getPriceList()
        } catch (nie: ConnectivityException.NoInternetException) {
            Snackbar.make(rootView, R.string.toast_no_internet, Snackbar.LENGTH_LONG)
                .setAction(R.string.retry) { showSubscriptionPossibilities(false) }.show()
            null
        } catch (ie: ConnectivityException.ImplementationException) {
            Snackbar.make(rootView, R.string.toast_unknown_error, Snackbar.LENGTH_LONG)
                .setAction(R.string.retry) { showSubscriptionPossibilities(false) }.show()
            null
        }
    }

    private fun showRegistrationSuccessful() {
        log.debug("showLoginRegistrationSuccessful")
        showFragment(RegistrationSuccessfulFragment())
    }

    private fun showPasswordRequest(
        showSubscriptionId: Boolean = false,
        invalidId: Boolean = false,
        invalidMail: Boolean = false
    ) {
        log.debug("showPasswordRequest")
        showFragment(
            PasswordRequestFragment.create(
                invalidId = invalidId,
                invalidMail = invalidMail,
                showSubscriptionId = showSubscriptionId
            )
        )
    }

    private fun showPasswordMailSent() {
        log.debug("showPasswordMailSent")
        showFragment(PasswordEmailSentFragment())
    }

    private fun showPasswordRequestNoMail() {
        log.debug("showPasswordRequestNoMail")
        showFragment(PasswordRequestNoMailFragment())
    }

    private fun showNamesMissing() {
        log.debug("showNamesMissing")
        showFragment(NamesMissingFragment())
    }

    fun done() {
        log.debug("done")
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

    private fun showSubscriptionAddress(
        cityInvalid: Boolean = false,
        countryInvalid: Boolean = false,
        postcodeInvalid: Boolean = false,
        streetInvalid: Boolean = false,
        nameTooLong: Boolean = false,
        firstNameEmpty: Boolean = false,
        firstNameInvalid: Boolean = false,
        surnameEmpty: Boolean = false,
        surnameInvalid: Boolean = false
    ) {
        log.debug("showSubscriptionAddress")
        showFragment(
            SubscriptionAddressFragment.createInstance(
                nameTooLong = nameTooLong,
                firstNameEmpty = firstNameEmpty,
                firstNameInvalid = firstNameInvalid,
                surnameEmpty = surnameEmpty,
                surnameInvalid = surnameInvalid,
                cityInvalid = cityInvalid,
                countryInvalid = countryInvalid,
                postcodeInvalid = postcodeInvalid,
                streetInvalid = streetInvalid
            )
        )
    }

    private fun showSubscriptionAccount(
        mailInvalid: Boolean = false,
        subscriptionInvalid: Boolean = false
    ) {
        log.debug("showSubscriptionAccount")
        showFragment(
            SubscriptionAccountFragment.createInstance(
                mailInvalid = mailInvalid,
                subscriptionInvalid = subscriptionInvalid
            )
        )
    }

    private fun showSubscriptionBank(
        accountHolderInvalid: Boolean = false,
        ibanEmpty: Boolean = false,
        ibanInvalid: Boolean = false,
        ibanNoSepa: Boolean = false
    ) {
        log.debug("showSubscriptionBank")
        showFragment(
            SubscriptionBankFragment.createInstance(
                accountHolderInvalid = accountHolderInvalid,
                ibanEmpty = ibanEmpty,
                ibanInvalid = ibanInvalid,
                ibanNoSepa = ibanNoSepa
            )
        )
    }
}