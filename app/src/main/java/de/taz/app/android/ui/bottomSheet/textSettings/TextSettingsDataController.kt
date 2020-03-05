package de.taz.app.android.ui.bottomSheet.textSettings

import android.content.SharedPreferences
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.singletons.SETTINGS_TEXT_FONT_SIZE
import de.taz.app.android.singletons.SETTINGS_TEXT_FONT_SIZE_DEFAULT
import de.taz.app.android.singletons.SETTINGS_TEXT_NIGHT_MODE
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import de.taz.app.android.util.SharedPreferenceStringLiveData

class TextSettingsDataController : BaseDataController(), TextSettingsContract.DataController {

    private lateinit var textSizeLiveData: SharedPreferenceStringLiveData
    private lateinit var nightModeLiveData: SharedPreferenceBooleanLiveData

    override fun observeNightMode(lifecycleOwner: LifecycleOwner, block: (Boolean) -> Unit) {
        nightModeLiveData.observe(lifecycleOwner, Observer(block))
    }

    override fun observeTextSize(lifecycleOwner: LifecycleOwner, block: (String) -> Unit) {
        textSizeLiveData.observe(lifecycleOwner, Observer(block))
    }

    override fun setNightMode(activated: Boolean) {
        nightModeLiveData.postValue(activated)
    }

    override fun setTextSizePercent(percent: String) {
        textSizeLiveData.postValue(percent)
    }

    override fun getTextSizePercent(): String {
        return textSizeLiveData.value ?: SETTINGS_TEXT_FONT_SIZE_DEFAULT
    }

    override fun setPreferences(preferences: SharedPreferences) {
        textSizeLiveData =
            SharedPreferenceStringLiveData(
                preferences,
                SETTINGS_TEXT_FONT_SIZE,
                SETTINGS_TEXT_FONT_SIZE_DEFAULT
            )

        nightModeLiveData =
            SharedPreferenceBooleanLiveData(preferences, SETTINGS_TEXT_NIGHT_MODE, false)
    }
}