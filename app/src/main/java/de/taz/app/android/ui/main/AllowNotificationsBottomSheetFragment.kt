package de.taz.app.android.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.commitNow
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.R
import de.taz.app.android.monkey.doNotFlattenCorners

class AllowNotificationsBottomSheetFragment :
    BottomSheetDialogFragment(R.layout.fragment_container), AllowNotificationsFragmentCallback {

    companion object {
        const val TAG = "allowNotifications"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.commitNow {
                add(R.id.fragment_container, AllowNotificationsFragment.newInstance(false))
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            if (this is BottomSheetDialog) {
                behavior.apply {
                    doNotFlattenCorners()
                    state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // this removes the translucent status of the status bar which causes some weird flickering
        // FIXME (johannes): refactor to get see why the flag is deprecated and move to general style
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    }

    override fun onAllowNotificationsDone() {
        dismiss()
    }
}