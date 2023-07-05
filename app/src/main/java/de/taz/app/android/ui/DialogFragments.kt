package de.taz.app.android.ui

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
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

