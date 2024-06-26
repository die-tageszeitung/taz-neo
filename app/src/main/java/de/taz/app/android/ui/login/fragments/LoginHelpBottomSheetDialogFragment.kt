package de.taz.app.android.ui.login.fragments

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentBottomSheetLoginHelpBinding
import de.taz.app.android.monkey.setBehaviorStateOnLandscape
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker

// FIXME: maybe use FullscreenViewBindingBottomSheetFragment
class LoginHelpBottomSheetDialogFragment: ViewBindingBottomSheetFragment<FragmentBottomSheetLoginHelpBinding>() {

    companion object {
        const val TAG = "LoginHelpBottomSheetDialogFragment"
    }

    private lateinit var tracker: Tracker

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.apply {
            actionPositive.setOnClickListener {
                dismiss()
            }

            actionMoreInfo.setOnClickListener {
                openFAQ()
                dismiss()
            }

            buttonClose.setOnClickListener {
                dismiss()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setBehaviorStateOnLandscape(BottomSheetBehavior.STATE_EXPANDED)
    }

    override fun onResume() {
        super.onResume()

        tracker.trackLoginHelpDialog()
    }

    private fun openFAQ() {
        val color = ContextCompat.getColor(requireContext(), R.color.colorAccent)
        try {
            val faqUri = Uri.parse(getString(R.string.faq_link))
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder().setToolbarColor(color).build()
                )
                .build()
                .apply { launchUrl(requireContext(), faqUri) }
        } catch (e: ActivityNotFoundException) {
            val toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
            toastHelper.showToast(R.string.toast_unknown_error)
        }
    }

}