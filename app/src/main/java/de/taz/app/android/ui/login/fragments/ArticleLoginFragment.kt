package de.taz.app.android.ui.login.fragments

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.databinding.FragmentArticleReadOnBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.ui.issueViewer.IssueViewerWrapperFragment
import de.taz.app.android.ui.login.LoginContract
import de.taz.app.android.ui.login.LoginViewModelState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ArticleLoginFragment : ViewBindingFragment<FragmentArticleReadOnBinding>(),
    ActivityResultCallback<LoginContract.Output> {

    private var articleFileName: String? = null
    private val elapsedViewModel by viewModels<SubscriptionElapsedBottomSheetViewModel>()
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
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                elapsedViewModel.isElapsedFlow.collect { setUIElapsed(it) }
            }
        }
    }

    private suspend fun setUIElapsed(isElapsed: Boolean) {
        viewBinding.apply {
            if (isElapsed) {
                readOnLoginGroup.visibility = View.GONE
                readOnSeparatorLine.visibility = View.GONE
                readOnTrialSubscriptionBox.visibility = View.GONE
                readOnSwitchPrint2digiBox.visibility = View.GONE
                readOnExtendPrintWithDigiBox.visibility = View.GONE
                readOnElapsedGroup.visibility = View.VISIBLE

                sendButton.setOnClickListener {
                    elapsedViewModel.sendMessage(
                        messageToSubscriptionService.editText?.text.toString(),
                        letTheSubscriptionServiceContactYouCheckbox.isChecked
                    )
                }
                readOnElapsedTitle.text = elapsedViewModel.elapsedTitleStringFlow.first()
                readOnElapsedDescription.text = elapsedViewModel.elapsedDescriptionStringFlow.first()

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


            readOnTrialSubscriptionBoxButton.setOnClickListener {
                register()
            }

            readOnSwitchPrint2digiBoxButton.setOnClickListener {
                switchPrintToDigi()
            }

            readOnExtendPrintWithDigiBoxButton.setOnClickListener {
                extendPrintWithDigi()
            }
        }
    }

    private fun getUsername(): String = viewBinding.readOnUsername.text.toString().trim()
    private fun getPassword(): String = viewBinding.readOnPassword.text.toString()

    private fun login() = startLoginActivity(LoginViewModelState.LOGIN)
    private fun register() = startLoginActivity(LoginViewModelState.SUBSCRIPTION_REQUEST)
    private fun switchPrintToDigi() =
        startLoginActivity(LoginViewModelState.SWITCH_PRINT_2_DIGI_REQUEST)

    private fun extendPrintWithDigi() =
        startLoginActivity(LoginViewModelState.EXTEND_PRINT_WITH_DIGI_REQUEST)

    private fun startLoginActivity(status: LoginViewModelState) {
        activityResultLauncher.launch(
            LoginContract.Input(
                status = status,
                username = getUsername(),
                password = getPassword(),
                articleFileName = articleFileName,
            )
        )
        hideKeyBoard()
    }


    private fun hideKeyBoard() {
        (activity?.getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager)?.apply {
            val view = activity?.currentFocus ?: View(activity)
            hideSoftInputFromWindow(view.windowToken, 0)
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
                    IssueViewerWrapperFragment.instance(
                        issueViewerWrapperFragment.issuePublication,
                        result.articleFileName
                    )
                ).commit()
            }
        }
    }

}