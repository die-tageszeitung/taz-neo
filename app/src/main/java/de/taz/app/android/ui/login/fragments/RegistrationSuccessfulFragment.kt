package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentLoginRegistrationSuccessfulBinding
import de.taz.app.android.ui.login.LoginActivity
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.hideSoftInputKeyboard
import kotlinx.coroutines.runBlocking

class RegistrationSuccessfulFragment :
    LoginBaseFragment<FragmentLoginRegistrationSuccessfulBinding>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hideSoftInputKeyboard()

        runBlocking {
            if (!viewModel.backToArticle || viewModel.isElapsed()) {
                viewBinding.fragmentLoginConfirmDone.text = getString(
                    R.string.fragment_login_success_login_back_coverflow
                )
            }
        }

        viewBinding.fragmentLoginConfirmDone.setOnClickListener {
            if (viewModel.backToArticle) {
                (activity as? LoginActivity)?.done() ?: activity?.finish()
            } else {
                // go home
                MainActivity.start(requireContext())
            }
        }
    }
}