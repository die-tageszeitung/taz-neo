package de.taz.app.android.monkey

import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

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