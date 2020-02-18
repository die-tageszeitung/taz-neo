package de.taz.app.android.ui.login.fragments

import android.content.Intent
import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.SUBSCRIPTION_EMAIL_ADDRESS
import de.taz.app.android.ui.login.LoginViewModelState
import kotlinx.android.synthetic.main.fragment_login_subscription_taken.*

class SubscriptionTakenFragment: BaseFragment(R.layout.fragment_login_subscription_taken) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_login_subscription_taken_retry.setOnClickListener {
            viewModel.apply {
                resetPassword()
                resetUsername()
                status.postValue(LoginViewModelState.INITIAL)
            }
        }

        fragment_login_subscription_taken_email.setOnClickListener {
            writeEmail()
        }
    }

    private fun writeEmail(to: String = SUBSCRIPTION_EMAIL_ADDRESS) {
        val email = Intent(Intent.ACTION_SEND)
        email.putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        email.putExtra(Intent.EXTRA_SUBJECT, "") // TODO
        email.putExtra(Intent.EXTRA_TEXT, "") // TODO
        email.type = "message/rfc822"
        startActivity(Intent.createChooser(email, null))
    }


}