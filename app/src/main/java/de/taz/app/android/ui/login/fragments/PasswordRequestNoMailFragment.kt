package de.taz.app.android.ui.login.fragments

import android.os.Bundle
import android.widget.TextView
import de.taz.app.android.databinding.FragmentLoginForgotPasswordNoMailBinding

class PasswordRequestNoMailFragment :
    LoginBaseFragment<FragmentLoginForgotPasswordNoMailBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding.fragmentLoginForgotPasswordNoMailEmail.setOnClickListener {
            it as TextView
            writeEmail(it.text?.toString() ?: "")
        }
    }
}