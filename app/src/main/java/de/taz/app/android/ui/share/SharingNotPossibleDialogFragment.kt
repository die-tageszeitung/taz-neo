package de.taz.app.android.ui.share

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R
import de.taz.app.android.tracking.Tracker


class SharingNotPossibleDialogFragment : DialogFragment() {

    private lateinit var tracker: Tracker

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tracker.trackSharingNotPossibleDialog()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.dialog_sharing_not_possible_title))
            .setMessage(getString(R.string.dialog_sharing_not_possible_message))
            .setPositiveButton(getString(R.string.close_okay)) { dialog, _ ->
                dialog.dismiss()
            }
            .create()
    }
}

