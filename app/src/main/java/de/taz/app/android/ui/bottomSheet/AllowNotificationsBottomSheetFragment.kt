package de.taz.app.android.ui.bottomSheet

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import de.taz.app.android.R
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentAllowNotificationsBottomSheetBinding
import de.taz.app.android.monkey.doNotFlattenCorners
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.ui.settings.SettingsActivity
import de.taz.app.android.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.Date

class AllowNotificationsBottomSheetFragment :
    ViewBindingBottomSheetFragment<FragmentAllowNotificationsBottomSheetBinding>() {

    private val viewModel by viewModels<SettingsViewModel>()
    private lateinit var generalDataStore: GeneralDataStore

    override fun onStart() {
        super.onStart()

        val isLandscape =
            this.resources.displayMetrics.heightPixels < this.resources.displayMetrics.widthPixels
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        if (isLandscape) {
            behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            val currentDate = simpleDateFormat.format(Date())
            generalDataStore.allowNotificationsLastTimeShown.set(currentDate)
        }

        viewBinding.fragmentBottomSheetNotificationsYes.setOnClickListener {
            lifecycleScope.launch {
                tryToSetNotificationsEnabled()
            }
            dismiss()
        }
        viewBinding.fragmentBottomSheetNotificationsNotNow.setOnClickListener {
            dismiss()
        }
        viewBinding.fragmentBottomSheetNotificationsNo.setOnClickListener {
            lifecycleScope.launch {
                generalDataStore.allowNotificationsDoNotShowAgain.set(true)
            }
            dismiss()
        }

        viewBinding.buttonClose.setOnClickListener { dismiss() }

        (dialog as BottomSheetDialog).behavior.doNotFlattenCorners()
    }

    private suspend fun tryToSetNotificationsEnabled() {
        val result = viewModel.setNotificationsEnabled(true)
        val systemAllows =
            NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
        if (!result || !systemAllows) {
            goToSettings()
        }
    }

    private fun goToSettings() {
        Intent(requireActivity(), SettingsActivity::class.java).apply {
            startActivity(this)
        }
    }
}