package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import de.taz.app.android.databinding.FragmentLoginSubscriptionInactiveBinding

class SubscriptionInactiveFragment : LoginBaseFragment<FragmentLoginSubscriptionInactiveBinding>() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentLoginSubscriptionInactiveEmail.setOnClickListener {
            writeEmail()
        }

    }

}