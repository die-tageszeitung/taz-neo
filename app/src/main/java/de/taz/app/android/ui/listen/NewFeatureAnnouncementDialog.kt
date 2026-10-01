package de.taz.app.android.ui.listen

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.NewFeatureAnnouncementListenBinding
import de.taz.app.android.monkey.getApplicationScope
import kotlinx.coroutines.launch

class NewFeatureAnnouncementDialog : DialogFragment() {

    companion object {
        const val TAG = "NewFeatureAnnouncementDialog"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null && !fragmentManager.isStateSaved) {
                NewFeatureAnnouncementDialog().show(fragmentManager, TAG)
            }
        }
    }

    private lateinit var binding: NewFeatureAnnouncementListenBinding
    private lateinit var generalDataStore: GeneralDataStore
    private var dismissedByUser = false

    override fun onAttach(context: Context) {
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        super.onAttach(context)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = NewFeatureAnnouncementListenBinding.inflate(layoutInflater)

        val dialog = MaterialAlertDialogBuilder(
            requireContext(),
            R.style.ThemeOverlay_App_MaterialAlertDialog_Fullscreen_Transparent,
        )
            .setView(binding.root)
            .create()

        dialog.window?.apply {
            setDimAmount(0.2f)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }

        binding.buttonClose.setOnClickListener {
            dismissedByUser = true
            dismiss()
        }

        binding.buttonTryDirect.setOnClickListener {
            dismissedByUser = true
            val intent = Intent(requireContext(), ListenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            // Avoid blinking of the "new" badge by directly setting it as clicked
            generalDataStore.markListenTabAsClicked()
            dismiss()
            startActivity(intent)
        }

        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.let { window ->
            // Enforce layout constraints (Full width, wrapping height)
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )

            val params = window.attributes

            // 1. Programmatically position it at the bottom center
            params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL

            // 2. Safely grab Activity's tab bar view
            val bottomNavigationView = activity?.findViewById<View>(R.id.navigation_bottom)

            // 3. Wait for the tab bar to draw to get its true layout height
            bottomNavigationView?.post {
                params.y = bottomNavigationView.height // Shifting it up by the bar height
                window.attributes = params
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        dismissedByUser = true
        super.onCancel(dialog)
    }

    override fun onDismiss(dialog: DialogInterface) {
        getApplicationScope().launch {
            generalDataStore.showNewListenAnnouncement.set(!dismissedByUser)
            generalDataStore.showListenTabBadge.set(dismissedByUser)
        }
        super.onDismiss(dialog)
    }
}
