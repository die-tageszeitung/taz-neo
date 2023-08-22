package de.taz.app.android.ui.login.fragments

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.SubscriptionStatus
import de.taz.app.android.databinding.FragmentLoginConfirmEmailBinding
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.util.hideSoftInputKeyboard
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ConfirmEmailFragment : LoginBaseFragment<FragmentLoginConfirmEmailBinding>() {

    private lateinit var authHelper: AuthHelper

    companion object {
        private const val EMAIL_ALREADY_CONFIRMED_WAITING_TIME_MS = 1_000L
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        authHelper = AuthHelper.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideSoftInputKeyboard()

        lifecycleScope.launch {
            viewModel.setPolling(startPolling = !viewModel.isElapsed())
        }

        if (!viewModel.backToArticle) {
            if (viewModel.backToHome) {
                viewBinding.fragmentLoginConfirmDone.text = getString(
                    R.string.fragment_login_success_login_back_coverflow
                )
            } else {
                viewBinding.fragmentLoginConfirmDone.text = getString(
                    R.string.fragment_login_success_login_back_settings
                )
            }
        }

        runBlocking {
            if (viewModel.isElapsed()) {
                viewBinding.fragmentLoginConfirmDone.text = getString(
                    R.string.fragment_login_success_login_back_coverflow
                )
                viewBinding.fragmentLoginConfirmEmailHeader.text = getString(
                    R.string.fragment_login_confirm_email_elapsed_header
                )
            }
        }
        /**
        * This is a workaround/hack for some unknown behavior of the [LoginViewModel].
        * Somehow the [LoginViewModelState.REGISTRATION_SUCCESSFUL] is not triggered 
        * during the probeabo flow and we remain on the [ConfirmEmailFragment] forever - 
        * even if the user confirmed her E-Mail or ordered a probeabo providing credentials
        * for an already verified taz-ID.
        * To prevent users from being stuck we provide this workaround that is showing 
        * a confirmation text to users without requiring to fix the [LoginViewModel] state handling 
        * internals.
        */
        lifecycleScope.launch {
            authHelper.status.asFlow().collect { status ->
                if (status == AuthStatus.valid) {
                    if (emailAlreadyConfirmed()) {
                        viewBinding.fragmentLoginConfirmEmailHeader.text = getString(
                            R.string.fragment_login_confirm_email_already_confirmed_header
                        )
                    } else {
                        viewBinding.fragmentLoginConfirmEmailHeader.text = getString(
                            R.string.fragment_login_confirm_email_confirmed_header
                        )
                    }
                }
            }
        }

        viewBinding.fragmentLoginConfirmDone.setOnClickListener {
            viewModel.setDone()
        }
    }

    /**
     * If the time between we first encountered the [SubscriptionStatus.waitForMail] status and
     * [AuthStatus.valid] is below [EMAIL_ALREADY_CONFIRMED_WAITING_TIME_MS] we can assume that the mail was already confirmed
     * (properly an unlinked taz id).
     */
    private fun emailAlreadyConfirmed(): Boolean {
        val now = System.currentTimeMillis()
        return (now - viewModel.waitForMailSinceMs < EMAIL_ALREADY_CONFIRMED_WAITING_TIME_MS)
    }
}