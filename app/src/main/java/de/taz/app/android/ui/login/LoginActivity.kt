package de.taz.app.android.ui.login

import android.os.Bundle
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.monkey.getViewModel
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.login.fragments.*
import de.taz.app.android.util.ToastHelper

const val LOGIN_EXTRA_USERNAME: String = "LOGIN_EXTRA_USERNAME"
const val LOGIN_EXTRA_PASSWORD: String = "LOGIN_EXTRA_PASSWORD"


class LoginActivity(
    private val toastHelper: ToastHelper = ToastHelper.getInstance(),
    private val issueRepository: IssueRepository = IssueRepository.getInstance()
) : FragmentActivity() {

    private var username: String? = null
    private var password: String? = null

    private lateinit var viewModel: LoginViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        username = intent.getStringExtra(LOGIN_EXTRA_USERNAME)
        password = intent.getStringExtra(LOGIN_EXTRA_PASSWORD)

        viewModel = getViewModel { LoginViewModel(username, password) }

        viewModel.status.observe(this, Observer { loginViewModelState: LoginViewModelState? ->
            when (loginViewModelState) {
                LoginViewModelState.INITIAL ->
                    showLoginForm()
                LoginViewModelState.CREDENTIALS_CHECKING,
                LoginViewModelState.SUBSCRIPTION_CHECKING ->
                    showLoadingScreen()
                LoginViewModelState.CREDENTIALS_INVALID ->
                    showCredentialsInvalid()
                LoginViewModelState.CREDENTIALS_MISSING ->
                    showMissingCredentials()
                LoginViewModelState.SUBSCRIPTION_ELAPSED ->
                    showSubscriptionElapsed()
                LoginViewModelState.SUBSCRIPTION_INVALID ->
                    showSubscriptionInvalid()
                LoginViewModelState.SUBSCRIPTION_MISSING ->
                    showSubscriptionMissing()
                LoginViewModelState.DONE ->
                    finish()
            }
        })

        viewModel.noInternet.observe(this, Observer {
            if (it) {
                toastHelper.showNoConnectionToast()
            }
        })
    }

    private fun showLoginForm() {
        showFragment(
            LoginFragment.create(
                username = viewModel.getUsername(),
                password = viewModel.getPassword()
            )
        )
    }

    private fun showLoadingScreen() {
        showFragment(LoadingScreenFragment())

    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.activity_login_fragment_placeholder, fragment)
            .commit()
    }

    private fun showSubscriptionElapsed() {
        showFragment(SubscriptionInactiveFragment())
    }

    private fun showSubscriptionMissing() {
        showFragment(SubscriptionMissingFragment())
    }

    private fun showMissingCredentials() {
        showFragment(MissingCredentialsFragment())
    }

    private fun showCredentialsInvalid() {
        showFragment(
            LoginFragment.create(
                username = viewModel.getUsername(),
                password = viewModel.getPassword(),
                usernameErrorId = R.string.login_error_unknown_credentials
            )
        )
    }

    private fun showSubscriptionInvalid() = showCredentialsInvalid()

    private fun done() {}

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

}