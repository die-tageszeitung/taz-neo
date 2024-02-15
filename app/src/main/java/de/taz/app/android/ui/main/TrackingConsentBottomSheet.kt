package de.taz.app.android.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.core.os.bundleOf
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.R
import de.taz.app.android.monkey.doNotFlattenCorners

class TrackingConsentBottomSheet : BottomSheetDialogFragment(R.layout.fragment_container),
    TrackingConsentFragmentCallback, AllowNotificationsFragmentCallback {

    companion object {
        const val TAG = "trackingConsent"
        private const val ARGUMENT_SHOW_ALLOW_NOTIFICATIONS = "showAllowNotifications"

        fun newInstance(showAllowNotifications: Boolean) = TrackingConsentBottomSheet().apply {
            arguments = bundleOf(
                ARGUMENT_SHOW_ALLOW_NOTIFICATIONS to showAllowNotifications
            )
        }
    }

    private val showAllowNotifications: Boolean
        get() = arguments?.getBoolean(ARGUMENT_SHOW_ALLOW_NOTIFICATIONS) ?: false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.commitNow {
                add(R.id.fragment_container, newTrackingConsentFragment())
            }
            isCancelable = false
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            if (this is BottomSheetDialog) {
                onBackPressedDispatcher.addCallback(
                    this@TrackingConsentBottomSheet,
                    onBackPressedCallback
                )
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

    private val onBackPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (childFragmentManager.backStackEntryCount > 0) {
                childFragmentManager.popBackStack()
            }
            isEnabled = false
        }
    }

    override fun onAllowNotificationsDone() {
        dismiss()
    }

    override fun onTrackingConsentDone() {
        if (showAllowNotifications) {
            childFragmentManager.commit {
                replace(R.id.fragment_container, newAllowNotificationsFragment())
                addToBackStack(null)
            }
            onBackPressedCallback.isEnabled = true
            isCancelable = true
        } else {
            dismiss()
        }
    }

    private fun newTrackingConsentFragment(): TrackingConsentFragment {
        return if (showAllowNotifications) {
            TrackingConsentFragment.newInstance(
                R.string.fragment_tracking_and_notifications_title, 1, 2
            )
        } else {
            TrackingConsentFragment.newInstance()
        }
    }

    private fun newAllowNotificationsFragment(): AllowNotificationsFragment {
        return AllowNotificationsFragment.newInstance(
            R.string.fragment_tracking_and_notifications_title, 2, 2
        )
    }
}
