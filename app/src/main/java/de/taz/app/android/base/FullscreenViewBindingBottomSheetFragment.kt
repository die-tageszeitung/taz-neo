package de.taz.app.android.base

import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import de.taz.app.android.util.Log

/**
 * FullscreenViewBindingBottomSheetFragment provides functionality to make a bottom sheet fragment expand to the full screen.
 *
 * Note that this class uses the internal design_bottom_sheet view created by BottomSheetDialogFragment.
 * This is necessary because there is no public API to access this view directly.
 */
abstract class FullscreenViewBindingBottomSheetFragment<VIEW_BINDING : ViewBinding> :
    ViewBindingBottomSheetFragment<VIEW_BINDING>() {

    protected val log by Log

    override fun onStart() {
        super.onStart()

        val dialog = (this.dialog as? BottomSheetDialog) ?: return

        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            // isFitToContents = false
        }

        // Access the internal design_bottom_sheet view that is created by BottomSheetDialogFragment
        val internalDesignBottomSheet =
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)

        if (internalDesignBottomSheet == null) {
            log.warn("DesignBottomSheet with id design_bottom_sheet not found in dialog views")
        }

        internalDesignBottomSheet?.apply {
            // Make the BottomSheet always take the full screen
            updateLayoutParams<ViewGroup.LayoutParams> {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }

            // Clip the content to the rounded BottomSheet corners.
            // Required to prevent weird artefacts when scrolling content out of the BottomSheet,
            // or when the content view has a different background
            // Note: this does not seem to work on older devices
            clipToOutline = true
        }
    }
}

