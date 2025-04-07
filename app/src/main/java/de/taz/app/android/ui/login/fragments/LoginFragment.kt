package de.taz.app.android.ui.login.fragments

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import de.taz.app.android.BuildConfig
import de.taz.app.android.WEBVIEW_HTML_FILE_DATA_POLICY
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentLoginBinding
import de.taz.app.android.listener.OnEditorActionDoneListener
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.WebViewActivity
import de.taz.app.android.util.hideSoftInputKeyboard

class LoginFragment : LoginBaseFragment<FragmentLoginBinding>() {

    private lateinit var tracker: Tracker
    private lateinit var generalDataStore: GeneralDataStore

    @StringRes
    private var usernameErrorId: Int? = null
    private var usernameErrorMessage: String? = null

    @StringRes
    private var passwordErrorId: Int? = null

    companion object {
        fun create(
            @StringRes usernameErrorId: Int? = null,
            @StringRes passwordErrorId: Int? = null
        ): LoginFragment {
            return LoginFragment().also {
                it.usernameErrorId = usernameErrorId
                it.passwordErrorId = passwordErrorId
            }
        }
        fun create(
            errorMessage: String? = null,
        ): LoginFragment {
            return LoginFragment().also {
                it.usernameErrorMessage = errorMessage
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = Tracker.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.apply {
            viewModel.password?.let {
                fragmentLoginPassword.setText(it)
            }

            fragmentLoginUsername.apply {
                setText(viewModel.username ?: viewModel.subscriptionId?.toString())
            }

            usernameErrorMessage?.let {
                fragmentLoginUsernameLayout.error = it
            }

            usernameErrorId?.let {
                fragmentLoginUsernameLayout.error = getString(it)
                usernameErrorId = null
            }

            passwordErrorId?.let {
                fragmentLoginPasswordLayout.error = getString(it)
                passwordErrorId = null
            }

            if (BuildConfig.IS_LMD) {
                fragmentLoginSubscriptions.root.isVisible = false
            }
            fragmentLoginPasswordHelpForgot.setOnClickListener {
                viewModel.requestPasswordReset()
            }

            fragmentLoginUsernameHelp.setOnClickListener {
                LoginHelpBottomSheetDialogFragment().show(parentFragmentManager, LoginHelpBottomSheetDialogFragment.TAG)
            }

            fragmentLoginPassword.setOnEditorActionListener(
                OnEditorActionDoneListener {
                    login(this)
                }
            )

            fragmentLoginActionLogin.setOnClickListener {
                login(this)
            }

            fragmentLoginActionDataPolicy.apply {
                // Show data policy link only on free variants
                isGone = BuildConfig.IS_NON_FREE
                setOnClickListener {
                    showDataPolicy()
                }
            }

        }

        viewBinding.fragmentLoginSubscriptions.apply {

            fragmentLoginTrialSubscriptionBoxButton.setOnClickListener {
                viewModel.requestSubscription(viewBinding.fragmentLoginUsername.text.toString().trim().lowercase())
            }

            fragmentLoginSwitchPrint2digiBoxButton.setOnClickListener {
                viewModel.requestSwitchPrint2Digi()
            }

            fragmentLoginExtendPrintWithDigiBoxButton.setOnClickListener {
                viewModel.requestExtendPrintWithDigi()
            }
        }
    }

    private fun login(viewBinding: FragmentLoginBinding) {
        val username = viewBinding.fragmentLoginUsername.text.toString().trim().lowercase()
        val password = viewBinding.fragmentLoginPassword.text.toString()
        viewModel.login(username, password)
        hideSoftInputKeyboard()
    }

    private fun showDataPolicy() {
        activity?.apply {
            val intent = WebViewActivity.newIntent(this, WEBVIEW_HTML_FILE_DATA_POLICY)
            startActivity(intent)
        }
    }

    override fun onDestroyView() {
        viewBinding.fragmentLoginUsername.text?.let { viewModel.username = it.toString() }
        viewBinding.fragmentLoginPassword.text?.let { viewModel.password = it.toString() }
        super.onDestroyView()
    }

}