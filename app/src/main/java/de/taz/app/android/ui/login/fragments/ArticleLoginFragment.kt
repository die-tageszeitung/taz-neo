package de.taz.app.android.ui.login.fragments

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.databinding.FragmentArticleReadOnBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.issueViewer.IssueViewerWrapperFragment
import de.taz.app.android.ui.login.ACTIVITY_LOGIN_REQUEST_CODE
import de.taz.app.android.ui.login.LOGIN_EXTRA_REGISTER
import de.taz.app.android.ui.login.LoginActivity
import de.taz.app.android.ui.login.LoginContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
            lifecycleScope.launch(Dispatchers.Main) {
                if (authHelper.isElapsed()) {
                    readOnLoginGroup.visibility = View.GONE
                    readOnElapsedGroup.visibility = View.VISIBLE

                    readOnElapsedOrder.setOnClickListener {
                        activity?.startActivityForResult(Intent(activity, LoginActivity::class.java).apply {
                            putExtra(LOGIN_EXTRA_REGISTER, true)
                        }, ACTIVITY_LOGIN_REQUEST_CODE)
                    }
                    val elapsedOn =
                        DateHelper.stringToLongLocalizedString(authHelper.elapsedDateMessage.get())
                    readOnElapsedTitle.text =
                        elapsedOn?.let { getString(R.string.popup_login_elapsed_header, elapsedOn) }
                            ?: getString(R.string.popup_login_elapsed_header_no_date)
                } else {
                    // Set listeners of login buttons when not elapsed
                    readOnLoginButton.setOnClickListener {
                        login()
                    }

                    readOnPassword.setOnEditorActionListener(
                        OnEditorActionDoneListener(::login)
                    )

                    readOnTrialSubscriptionBoxButton.setOnClickListener {
                        register()
                    }
                }
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
     * After a login we replace the [IssueViewerWrapperFragment] with a new instance of the
     * regular issue
     */
    override fun onActivityResult(result: LoginContract.Output) {
        if (result.success && result.articleFileName != null) {
            var parentFragment = parentFragment
            while (parentFragment !is IssueViewerWrapperFragment) {
                parentFragment = requireNotNull(parentFragment?.parentFragment) {
                    "ArticleLoginFragment can only be used in a IssueViewer2Fragment hierarchy"
                }
            }

            val issueViewerWrapperFragment: IssueViewerWrapperFragment = parentFragment

            issueViewerWrapperFragment.parentFragmentManager.apply {
                beginTransaction().replace(
                    android.R.id.content,
                    IssueViewerWrapperFragment.instance(issueViewerWrapperFragment.issuePublication, result.articleFileName)
                ).commit()
            }
        }
    }
}