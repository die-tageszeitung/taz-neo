package de.taz.app.android.monkey

import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


fun BottomSheetDialogFragment.setBehaviorStateOnLandscape(@BottomSheetBehavior.StableState state: Int) {
    val isLandscape = resources.displayMetrics.heightPixels < resources.displayMetrics.widthPixels
    if (isLandscape) {
        setBehaviorState(state)
    }
}

fun BottomSheetDialogFragment.setBehaviorState(@BottomSheetBehavior.StableState state: Int) {
    (dialog as? BottomSheetDialog)?.apply {
        behavior.state = state
    }
}