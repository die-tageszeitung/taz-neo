package de.taz.app.android.ui.login

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.util.ToastHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val LOGIN_EXTRA_USERNAME: String = "LOGIN_EXTRA_USERNAME"
const val LOGIN_EXTRA_PASSWORD: String = "LOGIN_EXTRA_PASSWORD"


class LoginActivity(
    private val apiService: ApiService = ApiService.getInstance(),
    private val toastHelper: ToastHelper = ToastHelper.getInstance()
) : AppCompatActivity() {

    private var username: String? = null
    private var password: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_login)

        username = intent.getStringExtra(LOGIN_EXTRA_USERNAME)
        password = intent.getStringExtra(LOGIN_EXTRA_PASSWORD)

        showFragment(LoginFragment())

        username?.let { username ->
            password?.let { password ->
                login(username, password)
                showLoadingScreen()
            }
        }

    }

    private fun login(username: String, password: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            username.toIntOrNull()?.let {
                val subscriptionStatus = apiService.checkSubscriptionId(it, password)?.status

                when(subscriptionStatus) {
                    AuthStatus.tazIdNotLinked -> showEmailNecessary()
                    AuthStatus.elapsed -> showSubscriptionElapsed()
                    AuthStatus.notValid -> showUnknownSubscriptionId()
                    AuthStatus.valid -> done() // TODO loggedin?
                    null -> toastHelper.showNoConnectionToast()
                }

            } ?: run {

                val authTokenInfo = apiService.authenticate(username, password)

                when (authTokenInfo?.authInfo?.status) {
                    AuthStatus.valid -> done() // TODO
                    AuthStatus.notValid -> showUnknownCredentials()
                    AuthStatus.tazIdNotLinked -> showSubscriptionNeeded()
                    AuthStatus.elapsed -> showSubscriptionElapsed()
                    null -> toastHelper.showNoConnectionToast()
                }

            }
        }
    }

    private fun showLoadingScreen() {

    }

    private fun hideLoadingScreen() {

    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.activity_login_fragment_placeholder, fragment)
            .commit()
    }

    private fun showSubscriptionElapsed() {
        showFragment(SubscriptionInactiveFragment())
    }

    private fun showUnknownCredentials() {

    }

    private fun showSubscriptionNeeded() {

    }

    private fun showEmailNecessary() {
        showFragment(EmailRequiredFragment())
    }

    private fun showUnknownSubscriptionId() {
        showFragment(LoginFragment())
        toastHelper.makeToast(R.string.login_error_unknown_credentials)
    }

    private fun done() {

    }

}