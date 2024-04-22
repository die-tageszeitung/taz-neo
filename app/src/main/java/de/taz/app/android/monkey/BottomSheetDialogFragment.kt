package de.taz.app.android.monkey

import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


// FIXME (johannes): BottomSheetDialogFragments can be prevented from canceling simply by setting isCancelable = false
fun BottomSheetDialogFragment.preventDismissal() {

    // remove onclicklistener when clicking to greyed out area
    dialog?.window?.decorView?.findViewById<View>(
        com.google.android.material.R.id.touch_outside
    )?.setOnClickListener(null)

    // do not allow to slide fragment away
    dialog?.window?.decorView?.findViewById<View>(
        com.google.android.material.R.id.design_bottom_sheet
    )?.let { bottomSheetView ->
        BottomSheetBehavior.from(bottomSheetView).isHideable = false
    }

}

// TODO (johannes): On the current build the corners are flattened in full screen. re-evaluate what this function is intended to do and if it works
fun <V : View> BottomSheetBehavior<V>.doNotFlattenCorners() {
    val method = BottomSheetBehavior::class.java.getMethod(
        "disableShapeAnimations"
    )
    method.invoke(this)
}

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
