package de.taz.app.android.ui.bottomSheet.textSize

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import de.taz.app.android.util.Log
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.ui.settings.SettingsOuterFragment

const val MIN_TEXT_SIZE = 0
const val MAX_TEST_SIZE = 200

class TextSizePresenter : BasePresenter<TextSizeContract.View, TextSizeDataController>(
    TextSizeDataController::class.java
), TextSizeContract.Presenter {

    private val log by Log

    override fun onViewCreated(savedInstanceState: Bundle?) {
        val tazApiCssPreferences = getView()?.getMainView()?.getApplicationContext()?.getSharedPreferences(
            PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

        getView()?.apply {
            tazApiCssPreferences?.let {
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
        getView()?.getMainView()?.showMainFragment(SettingsOuterFragment())
    }

    override fun decreaseTextSize() {
        viewModel?.apply {
            val newSize = getTextSizePercent().toInt() - 10
            if (newSize >= MIN_TEXT_SIZE) {
                setTextSizePercent(newSize.toString())
            }
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