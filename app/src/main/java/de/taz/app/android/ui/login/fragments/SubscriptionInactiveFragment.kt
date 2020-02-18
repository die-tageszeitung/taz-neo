package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_subscription_inactive.*

class SubscriptionInactiveFragment : BaseFragment(R.layout.fragment_login_subscription_inactive) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_login_subscription_inactive_email.setOnClickListener {
            writeEmail()
        }

    }

}