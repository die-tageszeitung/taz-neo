package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_confirm_email.*

class ConfirmEmailFragment: BaseFragment(R.layout.fragment_login_confirm_email) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideKeyBoard()

        fragment_login_confirm_done.setOnClickListener {
            viewModel.startPolling()
        }
    }

}