package de.taz.app.android.ui.login.fragments

import android.content.Intent
import androidx.annotation.StringRes
import androidx.fragment.app.activityViewModels
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.SUBSCRIPTION_EMAIL_ADDRESS
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.ui.login.LoginViewModel
import de.taz.app.android.util.hideSoftInputKeyboard


abstract class LoginBaseFragment<VIEW_BINDING: ViewBinding>: ViewBindingFragment<VIEW_BINDING>() {
    protected val viewModel
        get() = activityViewModels<LoginViewModel>().value

    protected fun writeEmail(to: String = SUBSCRIPTION_EMAIL_ADDRESS) {
        val email = Intent(Intent.ACTION_SEND)
        email.putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        email.putExtra(Intent.EXTRA_SUBJECT, "")
        email.putExtra(Intent.EXTRA_TEXT, "")
        email.type = "message/rfc822"
        startActivity(Intent.createChooser(email, null))
    }

    override fun onDetach() {
        hideSoftInputKeyboard()
        super.onDetach()
    }

    fun showHelpDialog(@StringRes stringRes: Int) {
        context?.let {
            val dialog = MaterialAlertDialogBuilder(it)
                .setMessage(stringRes)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            dialog.show()
        }
    }

    protected fun back() {
        if (viewModel.backToArticle) {
            finish()
        } else {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    protected fun finish() {
        requireActivity().finish()
    }
}