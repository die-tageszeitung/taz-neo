package de.taz.app.android.ui.main

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.commitNow
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.R

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
                    state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }

    override fun onAllowNotificationsDone() {
        dismiss()
    }
}