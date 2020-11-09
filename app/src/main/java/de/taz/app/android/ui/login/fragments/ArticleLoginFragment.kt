package de.taz.app.android.ui.login.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.ui.login.*
import kotlinx.android.synthetic.main.fragment_article_read_on.*

class ArticleLoginFragment : Fragment(R.layout.fragment_article_read_on) {

    private var articleFileName: String? = null

    companion object {
        fun create(articleFileName: String): ArticleLoginFragment {
            val fragment = ArticleLoginFragment()
            fragment.articleFileName = articleFileName
            return fragment
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_article_read_on_login_button.setOnClickListener {
            login()
        }

        fragment_article_read_on_password.setOnEditorActionListener(
            OnEditorActionDoneListener(::login)
        )

        fragment_article_read_on_register_button.setOnClickListener {
            register()
        }
    }

    private fun login() {
        activity?.startActivityForResult(Intent(activity, LoginActivity::class.java).apply {
            putExtra(LOGIN_EXTRA_USERNAME, fragment_article_read_on_username.text.toString().trim())
            putExtra(LOGIN_EXTRA_PASSWORD, fragment_article_read_on_password.text.toString())
            putExtra(LOGIN_EXTRA_ARTICLE, articleFileName)
        }, ACTIVITY_LOGIN_REQUEST_CODE)
        hideKeyBoard()
    }

    private fun register() {
        activity?.startActivityForResult(Intent(activity, LoginActivity::class.java).apply {
            putExtra(LOGIN_EXTRA_USERNAME, fragment_article_read_on_username.text.toString().trim())
            putExtra(LOGIN_EXTRA_PASSWORD, fragment_article_read_on_password.text.toString())
            putExtra(LOGIN_EXTRA_REGISTER, true)
            putExtra(LOGIN_EXTRA_ARTICLE, articleFileName)
        }, ACTIVITY_LOGIN_REQUEST_CODE)
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