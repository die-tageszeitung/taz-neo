package de.taz.app.android.ui.bottomSheet.textSettings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.databinding.FragmentBottomSheetTextSizeBinding
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.settings.SettingsActivity

class TextSettingsFragment :
    BaseViewModelFragment<TextSettingsViewModel, FragmentBottomSheetTextSizeBinding>() {

    private lateinit var tracker: Tracker

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.fragmentBottomSheetTextSizeSettings.setOnClickListener {
            onSettingsSelected()
        }

        viewBinding.fragmentBottomSheetTextSizeDecrease.setOnClickListener {
            decreaseFontSize()
        }

        viewBinding.fragmentBottomSheetTextSizePercentageWrapper.setOnClickListener {
            resetFontSize()
        }

        viewBinding.fragmentBottomSheetTextSizeIncrease.setOnClickListener {
            increaseFontSize()
        }

        viewBinding.fragmentBottomSheetTextSizeNightMode.setOnClickListener {
            onNightModeChanged(
                !viewBinding.fragmentBottomSheetTextSizeNightModeSwitch.isChecked
            )
        }

        viewBinding.fragmentBottomSheetTextSizeNightModeSwitch.setOnClickListener {
            onNightModeChanged(
                viewBinding.fragmentBottomSheetTextSizeNightModeSwitch.isChecked
            )
        }

        viewModel.observeNightMode(viewLifecycleOwner) { activated ->
            setNightMode(activated)
        }

        viewModel.observeFontSize(viewLifecycleOwner) { textSizePercentage ->
            setFontSizePercentage(textSizePercentage)
        }
    }

    override fun onResume() {
        super.onResume()
        tracker.trackTextSettingsDialog()
    }

    private fun setNightMode(active: Boolean) {
        viewBinding.fragmentBottomSheetTextSizeNightModeSwitch.isChecked = active
    }

    private fun setFontSizePercentage(percent: String) {
        viewBinding.fragmentBottomSheetTextSizePercentage.text = "$percent%"
    }

    private fun onNightModeChanged(activated: Boolean) {
        viewModel.setNightMode(activated)
    }

    private fun onSettingsSelected() {
        Intent(requireActivity(), SettingsActivity::class.java).apply {
            startActivity(this)
        }
    }

    private fun decreaseFontSize() {
        viewModel.decreaseFontSize()
    }

    private fun resetFontSize() {
        viewModel.resetFontSize()
    }

    private fun increaseFontSize() {
        viewModel.increaseFontSize()
    }

}