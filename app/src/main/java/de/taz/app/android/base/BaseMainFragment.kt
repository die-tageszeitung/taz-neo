package de.taz.app.android.base

import android.content.Context
import androidx.viewbinding.ViewBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.hideSoftInputKeyboard

abstract class BaseMainFragment<VIEW_BINDING: ViewBinding>: ViewBindingFragment<VIEW_BINDING>() {

    private lateinit var tracker: Tracker

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    protected fun showSharingNotPossibleDialog() {
        context?.let {
            val dialog = MaterialAlertDialogBuilder(it)
                .setTitle(getString(R.string.dialog_sharing_not_possible_title))
                .setMessage(getString(R.string.dialog_sharing_not_possible_message))
                .setPositiveButton(getString(R.string.close_okay)) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()
            dialog.show()
            tracker.trackSharingNotPossibleDialog()
        }
    }

    override fun onDetach() {
        hideSoftInputKeyboard()
        super.onDetach()
    }
}