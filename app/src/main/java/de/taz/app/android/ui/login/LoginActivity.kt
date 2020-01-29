package de.taz.app.android.ui.login

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import de.taz.app.android.R

const val LOGIN_EXTRA_USERNAME: String = "LOGIN_EXTRA_USERNAME"
const val LOGIN_EXTRA_PASSWORD: String = "LOGIN_EXTRA_PASSWORD"


class LoginActivity : AppCompatActivity() {

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
        // TODO
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

}