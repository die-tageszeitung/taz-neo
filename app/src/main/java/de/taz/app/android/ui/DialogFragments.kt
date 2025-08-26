package de.taz.app.android.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R
import de.taz.app.android.dataStore.GeneralDataStore
import kotlinx.coroutines.launch


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

fun Fragment.showContinueReadSettingDialog() {
    ContinueReadSettingDialog().show(childFragmentManager, ContinueReadSettingDialog.TAG)
}

class ContinueReadSettingDialog : DialogFragment() {
    companion object {
        const val TAG = "ContinueReadSettingDialog"
    }
    private lateinit var generalDataStore: GeneralDataStore

    override fun onAttach(context: Context) {
        super.onAttach(context)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.fragment_bottom_sheet_continue_read_dialog_title)
            .setMessage(R.string.fragment_bottom_sheet_continue_read_dialog_message)
            .setPositiveButton(R.string.fragment_bottom_sheet_continue_read_dialog_positive_button) { dialog, _ ->
                lifecycleScope.launch {
                    generalDataStore.settingsContinueReadAskEachTime.set(false)
                    generalDataStore.settingsContinueRead.set(true)
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.fragment_bottom_sheet_continue_read_dialog_negative_button) { dialog, _ ->
                lifecycleScope.launch {
                    generalDataStore.settingsContinueReadAskEachTime.set(true)
                    dialog.dismiss()
                }
            }
            .create()
    }
}
fun Fragment.showAlwaysTitleSectionSettingDialog() {
    AlwaysTitleSectionSettingDialog().show(childFragmentManager, ContinueReadSettingDialog.TAG)
}

class AlwaysTitleSectionSettingDialog : DialogFragment() {
    companion object {
        const val TAG = "AlwaysTitleSectionSettingDialog"
    }
    private lateinit var generalDataStore: GeneralDataStore

    override fun onAttach(context: Context) {
        super.onAttach(context)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.fragment_bottom_sheet_always_title_section_dialog_title)
            .setMessage(R.string.fragment_bottom_sheet_always_title_section_dialog_message)
            .setPositiveButton(R.string.fragment_bottom_sheet_always_title_section_dialog_positive_button) { dialog, _ ->
                lifecycleScope.launch {
                    generalDataStore.settingsContinueReadAskEachTime.set(true)
                    generalDataStore.settingsContinueRead.set(false)
                    dialog.dismiss()
                }
            }
            .setNegativeButton(R.string.fragment_bottom_sheet_always_title_section_dialog_negative_button) { dialog, _ ->
                lifecycleScope.launch {
                    generalDataStore.settingsContinueReadAskEachTime.set(false)
                    dialog.dismiss()
                }
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