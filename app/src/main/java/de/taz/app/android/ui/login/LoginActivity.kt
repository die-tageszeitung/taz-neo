package de.taz.app.android.ui.login

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.PriceInfo
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.getViewModel
import de.taz.app.android.monkey.moveContentBeneathStatusBar
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.*
import de.taz.app.android.ui.login.fragments.*
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionAccountFragment
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionAddressFragment
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionBankFragment
import de.taz.app.android.ui.login.fragments.subscription.SubscriptionPriceFragment
import de.taz.app.android.ui.main.*
import de.taz.app.android.util.Log
import io.sentry.core.Sentry
import kotlinx.android.synthetic.main.activity_login.*
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val ACTIVITY_LOGIN_REQUEST_CODE: Int = 161
const val LOGIN_EXTRA_USERNAME: String = "LOGIN_EXTRA_USERNAME"
const val LOGIN_EXTRA_PASSWORD: String = "LOGIN_EXTRA_PASSWORD"
const val LOGIN_EXTRA_REGISTER: String = "LOGIN_EXTRA_REGISTER"
const val LOGIN_EXTRA_ARTICLE = "LOGIN_EXTRA_ARTICLE"

class LoginActivity : NightModeActivity(R.layout.activity_login) {

    private val log by Log

    private lateinit var viewModel: LoginViewModel

    private var article: String? = null

    private var apiService: ApiService? = null
    private var articleRepository: ArticleRepository? = null
    private var authHelper: AuthHelper? = null
    private var issueRepository: IssueRepository? = null
    private var sectionRepository: SectionRepository? = null
    private var toastHelper: ToastHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        apiService = ApiService.getInstance(applicationContext)
        articleRepository = ArticleRepository.getInstance(applicationContext)
        authHelper = AuthHelper.getInstance(applicationContext)
        issueRepository = IssueRepository.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)
        sectionRepository = SectionRepository.getInstance(applicationContext)

        view.moveContentBeneathStatusBar()

        article = intent.getStringExtra(LOGIN_EXTRA_ARTICLE)

        navigation_bottom.apply {
            itemIconTintList = null

            // hack to not auto select first item
            menu.getItem(0).isCheckable = false

            setOnNavigationItemSelectedListener {
                this@LoginActivity.apply {
                    val data = Intent()
                    data.putExtra(MAIN_EXTRA_TARGET, MAIN_EXTRA_TARGET_HOME)
                    setResult(Activity.RESULT_CANCELED, data)
                    finish()
                }
                true
            }
        }

        val register = intent.getBooleanExtra(LOGIN_EXTRA_REGISTER, false)
        val username = intent.getStringExtra(LOGIN_EXTRA_USERNAME)
        val password = intent.getStringExtra(LOGIN_EXTRA_PASSWORD)

        viewModel = getViewModel { LoginViewModel(application, username, password, register) }

        viewModel.backToArticle = article != null

        viewModel.status.observe(this) { loginViewModelState: LoginViewModelState? ->
            when (loginViewModelState) {
                LoginViewModelState.INITIAL -> {
                    viewModel.validCredentials = viewModel.isElapsed()
                    if (register) {
                        showSubscriptionPrice()
                    } else {
                        showLoginForm()
                    }
                }
                LoginViewModelState.LOADING -> {
                    showLoadingScreen()
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
                    showSubscriptionElapsed()
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
                    showSubscriptionPrice()
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
                    toastHelper?.showToast(R.string.something_went_wrong_try_later)
                    showLoginForm()
                }
                LoginViewModelState.REGISTRATION_EMAIL -> {
                    showConfirmEmail()
                    authHelper!!.elapsedButWaiting = viewModel.isElapsed()
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
                LoginViewModelState.SUBSCRIPTION_PRICE_INVALID -> showSubscriptionPrice(priceInvalid = true)
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
            }
        }

        viewModel.noInternet.observeDistinct(this) {
            if (it) {
                toastHelper?.showNoConnectionToast()
            }
        }

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
        loading_screen.visibility = View.GONE
    }

    private fun showLoadingScreen() = runOnUiThread {
        log.debug("showLoadingScreen")
        loading_screen.visibility = View.VISIBLE
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

    private fun showSubscriptionElapsed() {
        log.debug("showSubscriptionElapsed")
        showFragment(SubscriptionInactiveFragment())
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
        toastHelper?.showToast(R.string.login_error_unknown_credentials)
        showFragment(
            LoginFragment.create(
                usernameErrorId = R.string.login_error_unknown_credentials
            )
        )
    }

    private fun showSubscriptionInvalid() = showCredentialsInvalid()

    private fun showSubscriptionPrice(priceInvalid: Boolean = false) {
        log.debug("showLoginRequestTestSubscription")
        viewModel.status.postValue(LoginViewModelState.LOADING)
        lifecycleScope.launch(Dispatchers.IO) {
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

    /**
     * get priceList synchronous to give user feedback
     */
    private suspend fun getPriceList(): List<PriceInfo>? {
        return try {
            ApiService.getInstance(applicationContext).getPriceList()
        } catch (nie: ConnectivityException.NoInternetException) {
            view?.let {
                Snackbar.make(it, R.string.toast_no_internet, Snackbar.LENGTH_LONG)
                    .setAction(R.string.retry) { showSubscriptionPrice(false) }.show()
            } ?: ToastHelper.getInstance(applicationContext).showNoConnectionToast()
            null
        } catch (ie: ConnectivityException.ImplementationException) {
            view?.let {
                Snackbar.make(it, R.string.toast_unknown_error, Snackbar.LENGTH_LONG)
                    .setAction(R.string.retry) { showSubscriptionPrice(false) }.show()
            } ?: ToastHelper.getInstance(applicationContext).showSomethingWentWrongToast()
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

        val data = Intent()
        if (authHelper?.isLoggedIn() == true) {
            lifecycleScope.launch(Dispatchers.IO) {
                article = article?.replace("public.", "")

                article?.let {
                    data.putExtra(MAIN_EXTRA_TARGET, MAIN_EXTRA_TARGET_ARTICLE)
                    data.putExtra(MAIN_EXTRA_ARTICLE, article)
                } ?: run {
                    data.putExtra(MAIN_EXTRA_TARGET, MAIN_EXTRA_TARGET_HOME)
                }

                withContext(Dispatchers.Main) {
                    setResult(Activity.RESULT_OK, data)
                    finish()
                }
            }
        } else {
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
            view.findViewById<AppBarLayout>(R.id.app_bar_layout)?.setExpanded(true, false)
            hideLoadingScreen()
        }
    }

    override fun onBackPressed() {
        if (loading_screen.visibility == View.VISIBLE) {
            hideLoadingScreen()
        } else {
            if (supportFragmentManager.backStackEntryCount == 1) {
                setResult(Activity.RESULT_CANCELED)
                finish()
            } else {
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