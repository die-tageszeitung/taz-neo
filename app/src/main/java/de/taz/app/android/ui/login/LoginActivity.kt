package de.taz.app.android.ui.login

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.monkey.getViewModel
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.login.fragments.*
import de.taz.app.android.util.Log
import de.taz.app.android.util.ToastHelper
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

const val LOGIN_EXTRA_USERNAME: String = "LOGIN_EXTRA_USERNAME"
const val LOGIN_EXTRA_PASSWORD: String = "LOGIN_EXTRA_PASSWORD"


class LoginActivity(
    private val toastHelper: ToastHelper = ToastHelper.getInstance(),
    private val issueRepository: IssueRepository = IssueRepository.getInstance()
) : FragmentActivity(R.layout.activity_login) {

    private val log by Log

    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val username = intent.getStringExtra(LOGIN_EXTRA_USERNAME)
        val password = intent.getStringExtra(LOGIN_EXTRA_PASSWORD)

        viewModel = getViewModel { LoginViewModel(username, password) }

        viewModel.observeStatus(this) { loginViewModelState: LoginViewModelState? ->
            when (loginViewModelState) {
                LoginViewModelState.INITIAL ->
                    showLoginForm()
                LoginViewModelState.REGISTRATION_CHECKING,
                LoginViewModelState.CREDENTIALS_CHECKING,
                LoginViewModelState.SUBSCRIPTION_CHECKING -> {
                    showLoadingScreen()
                }
                LoginViewModelState.CREDENTIALS_EMAIL_LINKED -> {
                    showEmailAlreadyLinked()
                }
                LoginViewModelState.CREDENTIALS_INVALID -> {
                    showCredentialsInvalid()
                }
                LoginViewModelState.CREDENTIALS_MISSING -> {
                    showMissingCredentials()
                }
                LoginViewModelState.CREDENTIALS_MISSING_INVALID_EMAIL -> {
                    showMissingCredentials(invalidMail = true)
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
                    showLoginRequestTestSubscription()
                }
                LoginViewModelState.SUBSCRIPTION_REQUEST_INVALID_EMAIL -> {
                    showLoginRequestTestSubscription(invalidMail = true)
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
                LoginViewModelState.PASSWORD_REQUEST_ONGOING -> {
                    showLoadingScreen()
                }
                LoginViewModelState.REGISTRATION_EMAIL -> {
                    showConfirmEmail()
                }
                LoginViewModelState.REGISTRATION_SUCCESSFUL -> {
                    showRegistrationSuccessful()
                }
                LoginViewModelState.USERNAME_MISSING -> {
                    showLoginForm(usernameErrorId = R.string.login_username_error_empty)
                }
                LoginViewModelState.DONE -> {
                    done()
                }
            }
        }

        viewModel.observeNoInternet(this) {
            if (it) {
                toastHelper.showNoConnectionToast()
                hideLoadingScreen()
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

    private fun hideLoadingScreen() {
        log.debug("hideLoadingScreen")
        loading_screen.visibility = View.GONE
    }

    private fun showLoadingScreen() {
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

    private fun showSubscriptionElapsed() {
        log.debug("showSubscriptionElapsed")
        showFragment(SubscriptionInactiveFragment())
    }

    private fun showSubscriptionMissing(invalidId: Boolean = false) {
        log.debug("showSubscriptionMissing")
        showFragment(SubscriptionMissingFragment.create(invalidId))
    }

    private fun showSubscriptionTaken() {
        log.debug("showSubscriptionTaken")
        showFragment(SubscriptionTakenFragment())
    }

    private fun showMissingCredentials(invalidMail: Boolean = false) {
        log.debug("showMissingCredentials")
        showFragment(CredentialsMissingFragment.create(invalidMail))
    }

    private fun showCredentialsInvalid() {
        log.debug("showCredentialsInvalid")
        showFragment(
            LoginFragment.create(
                usernameErrorId = R.string.login_error_unknown_credentials
            )
        )
    }

    private fun showSubscriptionInvalid() = showCredentialsInvalid()

    private fun showLoginRequestTestSubscription(invalidMail: Boolean = false) {
        log.debug("showLoginRequestTestSubscription")
        showFragment(RequestTestSubscriptionFragment.create(invalidMail))
    }

    private fun showRegistrationSuccessful() {
        log.debug("showLoginRegistrationSuccessful")
        showFragment(RegistrationSuccessfulFragment())
    }

    private fun showPasswordRequest() {
        log.debug("showPasswordRequest")
        showFragment(PasswordRequestFragment())
    }

    private fun showPasswordMailSent() {
        log.debug("showPasswordRequest")
        showFragment(PasswordEmailSentFragment())
    }

    fun done() {
        log.debug("done")
        showLoadingScreen()
        runBlocking(Dispatchers.IO) {
            downloadLatestIssueMoments()
        }
        deletePublicIssues()
        finish()
    }

    @UiThread
    private suspend fun downloadLatestIssueMoments() {
        ApiService.getInstance().getIssuesByDate().forEach { issue ->
            issueRepository.save(issue)
            /*getView()?.getMainView()?.apply {
                setDrawerIssue(issue)
                getApplicationContext().let {
                    DownloadService.download(it, issue.moment)
                }
            }
             */
        }
    }

    @UiThread
    private fun deletePublicIssues() {
        issueRepository.deletePublicIssues()
    }

    private fun showFragment(fragment: Fragment) {
        val fragmentClassName = fragment::class.java.name

        supportFragmentManager.popBackStackImmediate(fragmentClassName, POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction().apply {
            replace(R.id.activity_login_fragment_placeholder, fragment)
            addToBackStack(fragmentClassName)
            commit()
        }
        hideLoadingScreen()
    }

    override fun onBackPressed() {
        if (loading_screen.visibility == View.VISIBLE) {
            hideLoadingScreen()
        } else {
            if (supportFragmentManager.backStackEntryCount == 1) {
                finish()
            } else {
                super.onBackPressed()
            }
        }
    }

}