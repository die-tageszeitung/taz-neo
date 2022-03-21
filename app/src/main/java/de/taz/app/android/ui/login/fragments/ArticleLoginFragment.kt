package de.taz.app.android.ui.login.fragments

import android.app.Activity
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.databinding.FragmentArticleReadOnBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.ui.issueViewer.IssueViewerFragment2
import de.taz.app.android.ui.login.LoginContract

class ArticleLoginFragment : ViewBindingFragment<FragmentArticleReadOnBinding>(),
    ActivityResultCallback<LoginContract.Output> {

    private lateinit var authHelper: AuthHelper

    private var articleFileName: String? = null

    private lateinit var activityResultLauncher: ActivityResultLauncher<LoginContract.Input>

    companion object {
        fun create(articleFileName: String): ArticleLoginFragment {
            val fragment = ArticleLoginFragment()
            fragment.articleFileName = articleFileName
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityResultLauncher = registerForActivityResult(LoginContract(), this)
        authHelper = AuthHelper.getInstance(requireContext().applicationContext)
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
        activityResultLauncher.launch(
            LoginContract.Input(
                register = register,
                username = getUsername(),
                password = getPassword(),
                articleFileName = articleFileName,
            )
        )
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

    /**
     * After a login we replace the IssueViewerFragment 2 with a new instance of the
     * regular issue
     */
    override fun onActivityResult(result: LoginContract.Output) {
        if (result.success && result.articleFileName != null) {
            var parentFragment = parentFragment
            while (parentFragment !is IssueViewerFragment2) {
                parentFragment = requireNotNull(parentFragment?.parentFragment) {
                    "ArticleLoginFragment can only be used in a IssueViewer2Fragment hierarchy"
                }
            }

            val issueViewerFragment2: IssueViewerFragment2 = parentFragment

            issueViewerFragment2.parentFragmentManager.apply {
                beginTransaction().replace(
                    android.R.id.content,
                    IssueViewerFragment2.instance(issueViewerFragment2.issuePublication, result.articleFileName)
                ).commit()
            }
        }
    }
}