package de.taz.app.android.ui.login.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.ui.login.LOGIN_EXTRA_PASSWORD
import de.taz.app.android.ui.login.LOGIN_EXTRA_USERNAME
import de.taz.app.android.ui.login.LoginActivity
import kotlinx.android.synthetic.main.fragment_article_read_on.*

class ArticleLoginFragment : Fragment(R.layout.fragment_article_read_on) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_article_read_on_login_button.setOnClickListener {
            login()
        }

        fragment_article_read_on_password.setOnEditorActionListener(
            object : TextView.OnEditorActionListener {
                override fun onEditorAction(
                    v: TextView?,
                    actionId: Int,
                    event: KeyEvent?
                ): Boolean {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        login()
                        return true
                    }
                    return false
                }
            }
        )
    }

    private fun login() {
        startActivity(Intent(activity, LoginActivity::class.java).apply {
            putExtra(LOGIN_EXTRA_USERNAME, fragment_article_read_on_username.text.toString())
            putExtra(LOGIN_EXTRA_PASSWORD, fragment_article_read_on_password.text.toString())
        })
        hideKeyBoard()
    }

    private fun hideKeyBoard() {
        activity?.apply {
            (getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager)?.apply {
                val view = activity?.currentFocus ?: View(activity)
                hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

}