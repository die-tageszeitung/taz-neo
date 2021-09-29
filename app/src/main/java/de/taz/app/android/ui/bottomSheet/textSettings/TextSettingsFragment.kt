package de.taz.app.android.ui.bottomSheet.textSettings

import android.content.Intent
import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.ui.settings.SettingsActivity
import kotlinx.android.synthetic.main.fragment_bottom_sheet_text_size.*

class TextSettingsFragment :
    BaseViewModelFragment<TextSettingsViewModel>(R.layout.fragment_bottom_sheet_text_size) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        fragment_bottom_sheet_text_size_settings?.setOnClickListener {
            onSettingsSelected()
        }

        fragment_bottom_sheet_text_size_text_size_decrease?.setOnClickListener {
            decreaseFontSize()
        }

        fragment_bottom_sheet_text_size_text_size_percentage_wrapper?.setOnClickListener {
            resetFontSize()
        }

        fragment_bottom_sheet_text_size_text_size_increase?.setOnClickListener {
            increaseFontSize()
        }

        fragment_bottom_sheet_text_size_night_mode?.setOnClickListener {
            onNightModeChanged(
                !fragment_bottom_sheet_text_size_night_mode_switch.isChecked
            )
        }

        fragment_bottom_sheet_text_size_night_mode_switch?.setOnClickListener {
            onNightModeChanged(
                fragment_bottom_sheet_text_size_night_mode_switch.isChecked
            )
        }

        viewModel.observeNightMode(this) { activated ->
            setNightMode(activated)
        }

        viewModel.observeFontSize(this) { textSizePercentage ->
            setFontSizePercentage(textSizePercentage)
        }
    }

    private fun setNightMode(active: Boolean) {
        fragment_bottom_sheet_text_size_night_mode_switch?.isChecked = active
    }

    private fun setFontSizePercentage(percent: String) {
        fragment_bottom_sheet_text_size_text_size_percentage?.text = "$percent%"
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