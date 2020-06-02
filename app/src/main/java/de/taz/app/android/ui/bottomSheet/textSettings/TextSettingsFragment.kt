package de.taz.app.android.ui.bottomSheet.textSettings

import android.os.Bundle
import android.view.View
import de.taz.app.android.R
import de.taz.app.android.base.ViewModelFragment
import de.taz.app.android.singletons.SETTINGS_TEXT_FONT_SIZE_DEFAULT
import de.taz.app.android.ui.settings.SettingsFragment
import kotlinx.android.synthetic.main.fragment_bottom_sheet_text_size.*

const val MIN_TEXT_SIZE = 30
const val MAX_TEST_SIZE = 200

class TextSettingsFragment :
    ViewModelFragment<TextSettingsViewModel>(R.layout.fragment_bottom_sheet_text_size) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        fragment_bottom_sheet_text_size_settings?.setOnClickListener {
            onSettingsSelected()
        }

        fragment_bottom_sheet_text_size_text_size_decrease?.setOnClickListener {
            decreaseTextSize()
        }

        fragment_bottom_sheet_text_size_text_size_percentage_wrapper?.setOnClickListener {
            resetTextSize()
        }

        fragment_bottom_sheet_text_size_text_size_increase?.setOnClickListener {
            increaseTextSize()
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

        viewModel.observeTextSize(this) { textSizePercentage ->
            setTextSizePercentage(textSizePercentage)
        }
    }

    private fun setNightMode(active: Boolean) {
        fragment_bottom_sheet_text_size_night_mode_switch?.isChecked = active
    }

    private fun setTextSizePercentage(percent: String) {
        fragment_bottom_sheet_text_size_text_size_percentage?.text = "$percent%"
    }

    private fun onNightModeChanged(activated: Boolean) {
        viewModel.setNightMode(activated)
    }

    private fun onSettingsSelected() {
        showMainFragment(SettingsFragment())
    }

    private fun decreaseTextSize() {
        viewModel.apply {
            val newSize = getTextSizePercent().toInt() - 10
            if (newSize >= MIN_TEXT_SIZE) {
                setTextSizePercent(newSize.toString())
            }
        }
    }

    private fun resetTextSize() {
        viewModel.apply {
            setTextSizePercent(SETTINGS_TEXT_FONT_SIZE_DEFAULT)
        }
    }

    private fun increaseTextSize() {
        viewModel.apply {
            val newSize = getTextSizePercent().toInt() + 10
            if (newSize <= MAX_TEST_SIZE) {
                setTextSizePercent(newSize.toString())
            }
        }
    }

}