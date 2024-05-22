package de.taz.app.android.ui.login.fragments

import android.app.Dialog
import android.content.Intent
import androidx.annotation.StringRes
import androidx.fragment.app.viewModels
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.SUBSCRIPTION_EMAIL_ADDRESS
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.ui.login.LoginBottomSheetFragment
import de.taz.app.android.ui.login.LoginViewModel
import de.taz.app.android.util.hideSoftInputKeyboard


abstract class LoginBaseFragment<VIEW_BINDING: ViewBinding>: ViewBindingFragment<VIEW_BINDING>() {
    protected val viewModel by viewModels<LoginViewModel>({ requireParentFragment() })

    private var helpDialog: Dialog? = null

    protected fun writeEmail(to: String = SUBSCRIPTION_EMAIL_ADDRESS) {
        val email = Intent(Intent.ACTION_SEND)
        email.putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
        email.putExtra(Intent.EXTRA_SUBJECT, "")
        email.putExtra(Intent.EXTRA_TEXT, "")
        email.type = "message/rfc822"
        startActivity(Intent.createChooser(email, null))
    }

    override fun onDetach() {
        // As our view is already null onDetach we hide the soft input keyboard from the parent fragment
        parentFragment?.hideSoftInputKeyboard()
        super.onDetach()
    }

    override fun onDestroy() {
        super.onDestroy()
        helpDialog?.dismiss()
        helpDialog = null
    }

    fun showHelpDialog(@StringRes stringRes: Int) {
        context?.let {
            helpDialog = MaterialAlertDialogBuilder(it)
                .setMessage(stringRes)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
                .apply {
                    show()
                }
        }
    }

    protected fun loginFlowDone() {
        (parentFragment as? LoginBottomSheetFragment)?.done()
    }

    protected fun loginFlowBack() {
        (parentFragment as? LoginBottomSheetFragment)?.back()
    }

    protected fun loginFlowCancel() {
        (parentFragment as? LoginBottomSheetFragment)?.cancel()
    }
}