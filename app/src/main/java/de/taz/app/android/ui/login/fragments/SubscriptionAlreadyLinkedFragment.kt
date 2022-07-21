package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.databinding.FragmentLoginSubscriptionAlreadyTakenBinding
import de.taz.app.android.ui.login.LoginViewModelState

class SubscriptionAlreadyLinkedFragment :
    LoginBaseFragment<FragmentLoginSubscriptionAlreadyTakenBinding>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentLoginSubscriptionAlreadyTakenInsertNew.setOnClickListener {
            viewModel.status.postValue(LoginViewModelState.SUBSCRIPTION_MISSING)
        }
        viewBinding.fragmentLoginSubscriptionAlreadyTakenContactEmail.setOnClickListener {
            writeEmail()
        }
    }

}