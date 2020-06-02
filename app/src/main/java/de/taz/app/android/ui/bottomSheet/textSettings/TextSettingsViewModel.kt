package de.taz.app.android.ui.bottomSheet.textSettings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.singletons.SETTINGS_TEXT_FONT_SIZE
import de.taz.app.android.singletons.SETTINGS_TEXT_FONT_SIZE_DEFAULT
import de.taz.app.android.singletons.SETTINGS_TEXT_NIGHT_MODE
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import de.taz.app.android.util.SharedPreferenceStringLiveData

class TextSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPreferences =
        application.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)

    private val textSizeLiveData = SharedPreferenceStringLiveData(
        sharedPreferences,
        SETTINGS_TEXT_FONT_SIZE,
        SETTINGS_TEXT_FONT_SIZE_DEFAULT
    )
    private val nightModeLiveData =
            SharedPreferenceBooleanLiveData(sharedPreferences, SETTINGS_TEXT_NIGHT_MODE, false)

    fun observeNightMode(lifecycleOwner: LifecycleOwner, block: (Boolean) -> Unit) {
        nightModeLiveData.observe(lifecycleOwner, Observer(block))
    }

    fun observeTextSize(lifecycleOwner: LifecycleOwner, block: (String) -> Unit) {
        textSizeLiveData.observe(lifecycleOwner, Observer(block))
    }

    fun setNightMode(activated: Boolean) {
        nightModeLiveData.postValue(activated)
    }

    fun setTextSizePercent(percent: String) {
        textSizeLiveData.postValue(percent)
    }

    fun getTextSizePercent(): String {
        return textSizeLiveData.value ?: SETTINGS_TEXT_FONT_SIZE_DEFAULT
    }
}