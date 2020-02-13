package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_login_request_test_subscription.*

class RequestTestSubscriptionFragment: Fragment(R.layout.fragment_login_request_test_subscription) {

    private var username: String? = null
    private var password: String? = null

    companion object {
        fun create(
            username: String? = null,
            password: String? = null
        ): RequestTestSubscriptionFragment {
            return RequestTestSubscriptionFragment().also {
                it.username = username
                it.password = password
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        username?.let {
            fragment_login_request_test_subscription_email_text.setText(it)
        }

    }

}