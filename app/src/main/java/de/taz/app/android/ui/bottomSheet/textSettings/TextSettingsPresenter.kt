package de.taz.app.android.ui.bottomSheet.textSettings

import android.content.Context
import android.os.Bundle
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.singletons.SETTINGS_TEXT_FONT_SIZE_DEFAULT
import de.taz.app.android.ui.settings.SettingsFragment

const val MIN_TEXT_SIZE = 30
const val MAX_TEST_SIZE = 200

class TextSizePresenter : BasePresenter<TextSettingsContract.View, TextSettingsDataController>(
    TextSettingsDataController::class.java
), TextSettingsContract.Presenter {

    override fun onViewCreated(savedInstanceState: Bundle?) {
        getView()?.apply {
            getMainView()?.applicationContext?.getSharedPreferences(
                PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)?.let {
                viewModel?.setPreferences(it)
            }

            viewModel?.observeNightMode(getLifecycleOwner()) { activated ->
                setNightMode(activated)
            }

            viewModel?.observeTextSize(getLifecycleOwner()) { textSizePercentage ->
                setTextSizePercentage(textSizePercentage)
            }
        }
    }

    override fun onNightModeChanged(activated: Boolean) {
        viewModel?.setNightMode(activated)
    }

    override fun onTextSizeChanged(percent: String) {
        viewModel?.setTextSizePercent(percent)
    }

    override fun onSettingsSelected() {
        getView()?.getMainView()?.showMainFragment(SettingsFragment())
    }

    override fun decreaseTextSize() {
        viewModel?.apply {
            val newSize = getTextSizePercent().toInt() - 10
            if (newSize >= MIN_TEXT_SIZE) {
                setTextSizePercent(newSize.toString())
            }
        }
    }

    override fun resetTextSize() {
        viewModel?.apply {
            setTextSizePercent(SETTINGS_TEXT_FONT_SIZE_DEFAULT)
        }
    }

    override fun increaseTextSize() {
        viewModel?.apply {
            val newSize = getTextSizePercent().toInt() + 10
            if (newSize <= MAX_TEST_SIZE) {
                setTextSizePercent(newSize.toString())
            }
        }
    }

}