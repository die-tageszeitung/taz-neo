package de.taz.app.android.ui.login.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.databinding.FragmentArticleReadOnBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.ui.login.*

class ArticleLoginFragment : ViewBindingFragment<FragmentArticleReadOnBinding>() {

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

        viewBinding.apply {
            readOnLoginButton.setOnClickListener {
                login()
            }

            readOnPassword.setOnEditorActionListener(
                OnEditorActionDoneListener(::login)
            )

            readOnRegisterButton.setOnClickListener {
                register()
            }
        }
    }

    private fun getUsername(): String = viewBinding.readOnUsername.text.toString().trim()
    private fun getPassword(): String = viewBinding.readOnPassword.text.toString()

    private fun login() = startLoginActivity(false)
    private fun register() = startLoginActivity(true)

    private fun startLoginActivity(register: Boolean) {
        activity?.startActivityForResult(Intent(activity, LoginActivity::class.java).apply {
            putExtra(LOGIN_EXTRA_USERNAME, getUsername())
            putExtra(LOGIN_EXTRA_PASSWORD, getPassword())
            putExtra(LOGIN_EXTRA_REGISTER, register)
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