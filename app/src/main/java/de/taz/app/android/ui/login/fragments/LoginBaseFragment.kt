package de.taz.app.android.ui.login.fragments

import android.app.Activity
import android.content.Intent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.SUBSCRIPTION_EMAIL_ADDRESS
import de.taz.app.android.ui.login.LoginViewModel


abstract class LoginBaseFragment(@LayoutRes layoutId: Int): Fragment(layoutId) {
    protected val viewModel
        get() = activityViewModels<LoginViewModel>().value

    protected fun hideKeyBoard() {
        activity?.apply {
            (getSystemService(Activity.INPUT_METHOD_SERVICE) as? InputMethodManager)?.apply {
                val view = activity?.currentFocus ?: View(activity)
                hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    protected fun writeEmail(to: String = SUBSCRIPTION_EMAIL_ADDRESS) {
        val email = Intent(Intent.ACTION_SEND)
        email.putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        email.putExtra(Intent.EXTRA_SUBJECT, "")
        email.putExtra(Intent.EXTRA_TEXT, "")
        email.type = "message/rfc822"
        startActivity(Intent.createChooser(email, null))
    }

    override fun onDetach() {
        hideKeyBoard()
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

}