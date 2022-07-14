package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentLoginConfirmEmailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ConfirmEmailFragment : LoginBaseFragment<FragmentLoginConfirmEmailBinding>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideKeyBoard()

        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.setPolling(startPolling = !viewModel.isElapsed())
        }

        if (!viewModel.backToArticle) {
            viewBinding.fragmentLoginConfirmDone.text = getString(
                R.string.fragment_login_success_login_back_settings
            )
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

        viewBinding.fragmentLoginConfirmDone.setOnClickListener {
            viewModel.setDone()
        }
    }

}