package de.taz.app.android.ui.login

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
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
) : FragmentActivity() {

    private val log by Log

    private var username: String? = null
    private var password: String? = null

    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        username = intent.getStringExtra(LOGIN_EXTRA_USERNAME)
        password = intent.getStringExtra(LOGIN_EXTRA_PASSWORD)

        viewModel = getViewModel { LoginViewModel(username, password) }

        showLoginForm()

        viewModel.status.observe(this, Observer { loginViewModelState: LoginViewModelState? ->
            when (loginViewModelState) {
                LoginViewModelState.INITIAL ->
                    showLoginForm()
                LoginViewModelState.CREDENTIALS_CHECKING,
                LoginViewModelState.SUBSCRIPTION_CHECKING ->
                    showLoadingScreen()
                LoginViewModelState.CREDENTIALS_INVALID -> {
                    resetPassword()
                    showCredentialsInvalid()
                }
                LoginViewModelState.CREDENTIALS_MISSING ->
                    showMissingCredentials()
                LoginViewModelState.SUBSCRIPTION_ELAPSED ->
                    showSubscriptionElapsed()
                LoginViewModelState.SUBSCRIPTION_INVALID -> {
                    resetPassword()
                    showSubscriptionInvalid()
                }
                LoginViewModelState.SUBSCRIPTION_MISSING ->
                    showSubscriptionMissing()
                LoginViewModelState.PASSWORD_MISSING ->
                    showLoginForm(passwordErrorId = R.string.login_password_error_empty)
                LoginViewModelState.SUBSCRIPTION_REQUEST -> {
                    showLoginRequestTestSubscription()
                }
                LoginViewModelState.SUBSCRIPTION_REQUESTING -> {
                    showLoadingScreen()
                }
                LoginViewModelState.USERNAME_MISSING ->
                    showLoginForm(usernameErrorId = R.string.login_username_error_empty)
                LoginViewModelState.USE_CREDENTIALS ->
                    // TODO show "please use tazId to login"
                    showLoginForm()
                LoginViewModelState.DONE ->
                    done()
            }
        })

        viewModel.noInternet.observe(this, Observer {
            if (it) {
                toastHelper.showNoConnectionToast()
            }
        })

    }

    private fun showLoginForm(
        @StringRes usernameErrorId: Int? = null,
        @StringRes passwordErrorId: Int? = null
    ) {
        log.debug("showLoginForm")
        showFragment(
            LoginFragment.create(
                username = viewModel.getUsername(),
                password = viewModel.getPassword(),
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

    private fun showSubscriptionElapsed() {
        log.debug("showSubscriptionElapsed")
        showFragment(SubscriptionInactiveFragment())
    }

    private fun showSubscriptionMissing() {
        log.debug("showSubscriptionMissing")
        showFragment(MissingSubscriptionFragment())
    }

    private fun showMissingCredentials() {
        log.debug("showMissingCredentials")
        showFragment(MissingCredentialsFragment())
    }

    private fun showCredentialsInvalid() {
        log.debug("showCredentialsInvalid")
        showFragment(
            LoginFragment.create(
                username = viewModel.getUsername(),
                password = viewModel.getPassword(),
                usernameErrorId = R.string.login_error_unknown_credentials
            )
        )
    }

    private fun showSubscriptionInvalid() = showCredentialsInvalid()

    private fun showLoginRequestTestSubscription() {
        log.debug("showLoginRequestTestSubscription")
        showFragment(RequestTestSubscriptionFragment())
    }

    private fun done() {
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
        supportFragmentManager.beginTransaction().apply {

            replace(R.id.activity_login_fragment_placeholder, fragment)

            commit()
        }
        hideLoadingScreen()
    }

    private fun resetPassword() {
        password = null
        viewModel.resetPassword()
    }

    private fun resetUsername() {
        username = null
        viewModel.resetUsername()
    }

    override fun onStop() {
        super.onStop()
        resetPassword()
        resetUsername()
    }

}