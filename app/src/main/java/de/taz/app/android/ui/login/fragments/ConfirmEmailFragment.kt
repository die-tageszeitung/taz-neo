package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_confirm_email.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ConfirmEmailFragment : LoginBaseFragment(R.layout.fragment_login_confirm_email) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideKeyBoard()

        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.setPolling(startPolling = !viewModel.isElapsed())
        }

        if (!viewModel.backToArticle) {
            fragment_login_confirm_done.text = getString(
                R.string.fragment_login_success_login_back_settings
            )
        }

        runBlocking {
            if (viewModel.isElapsed()) {
                fragment_login_confirm_done.text = getString(
                    R.string.fragment_login_success_login_back_coverflow
                )
                fragment_login_confirm_email_header.text = getString(
                    R.string.fragment_login_confirm_email_elapsed_header
                )
            }
        }

        fragment_login_confirm_done.setOnClickListener {
            viewModel.setDone()
        }
    }

}