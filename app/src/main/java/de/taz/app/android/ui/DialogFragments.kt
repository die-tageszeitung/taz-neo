package de.taz.app.android.ui

import android.app.Dialog
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R


fun Fragment.showNoInternetDialog() {
    NoInternetDialogFragment().show(childFragmentManager, NoInternetDialogFragment.TAG)
}

class NoInternetDialogFragment : DialogFragment() {
    companion object {
        const val TAG = "NoInternetDialog"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_no_internet_title)
            .setMessage(R.string.dialog_no_internet_message)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }
}

fun Fragment.showSdCardIssueDialog() {
    SdCardIssueDialogFragment().show(childFragmentManager, SdCardIssueDialogFragment.TAG)
}

fun FragmentActivity.showSdCardIssueDialog() {
    SdCardIssueDialogFragment().show(supportFragmentManager, SdCardIssueDialogFragment.TAG)
}

class SdCardIssueDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "SdCardIssueExceptionDialog"
    }

    override fun onStart() {
        super.onStart()
        requireDialog().apply {
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            // This is necessary to open the hyperlink in the dialog message:
            findViewById<TextView>(android.R.id.message).movementMethod =
                LinkMovementMethod.getInstance()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_sd_card_issue_title)
            .setMessage(R.string.dialog_sd_card_issue_message)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                dialog.dismiss()
                requireActivity().finish()
            }
            .create()
    }

}


class SimpleErrorDialog : DialogFragment() {

    companion object {
        const val TAG = "SimpleErrorDialog"
        private const val ARG_MESSAGE_RES_ID = "messageResId"

        fun newInstance(@StringRes messageResId: Int) = SimpleErrorDialog().apply {
            arguments = bundleOf(
                ARG_MESSAGE_RES_ID to messageResId
            )
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        val stringId = arguments?.getInt(ARG_MESSAGE_RES_ID) ?: R.string.toast_unknown_error
        require(stringId != 0)

        return MaterialAlertDialogBuilder(activity)
            .setMessage(stringId)
            .setPositiveButton(android.R.string.ok) { _, _ -> dismiss() }
            .create()
    }

}