package de.taz.app.android.base

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R
import de.taz.app.android.getTazApplication
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.bottomSheet.AddBottomSheetDialog
import de.taz.app.android.util.hideSoftInputKeyboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class BaseMainFragment<VIEW_BINDING: ViewBinding>: ViewBindingFragment<VIEW_BINDING>() {

    private lateinit var tracker: Tracker

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = getTazApplication().tracker
    }

    suspend fun showSharingNotPossibleDialog() {
        withContext(Dispatchers.Main) {
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
    }

    /**
     * show bottomSheet
     * @param fragment: The [Fragment] which will be shown in the BottomSheet
     */
    fun showBottomSheet(fragment: Fragment): BottomSheetDialogFragment {
        val addBottomSheet =
            if (fragment is BottomSheetDialogFragment) {
                fragment
            } else {
                AddBottomSheetDialog.newInstance(fragment)
            }
        addBottomSheet.show(childFragmentManager, null)
        return addBottomSheet
    }

    override fun onDetach() {
        hideSoftInputKeyboard()
        super.onDetach()
    }
}