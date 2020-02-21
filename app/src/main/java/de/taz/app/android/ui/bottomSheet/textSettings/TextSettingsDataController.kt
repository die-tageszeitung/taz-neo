package de.taz.app.android.ui.bottomSheet.textSettings

import android.content.SharedPreferences
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import de.taz.app.android.util.SharedPreferenceStringLiveData

class TextSettingsDataController: BaseDataController(), TextSettingsContract.DataController {

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
        return textSizeLiveData.value ?: "100"
    }

    override fun setPreferences(preferences: SharedPreferences) {
        textSizeLiveData =
            SharedPreferenceStringLiveData(
                preferences,
                "text_font_size",
                "100"
            )
        nightModeLiveData =
            SharedPreferenceBooleanLiveData(
                preferences,
                "text_night_mode",
                false
            )

    }
}