package de.taz.app.android.ui.settings

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.PREFERENCES_TAZAPICSS
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.util.KEEP_ISSUES_DOWNLOADED_DEFAULT
import de.taz.app.android.util.SharedPreferenceBooleanLiveData
import de.taz.app.android.util.SharedPreferenceStringLiveData

class SettingsDataController : BaseDataController(), SettingsContract.DataController {

    private lateinit var textSizeLiveData: SharedPreferenceStringLiveData
    private lateinit var nightModeLiveData: SharedPreferenceBooleanLiveData
    private lateinit var storedIssueNumberLiveData: SharedPreferenceStringLiveData

    override fun observeNightMode(lifecycleOwner: LifecycleOwner, block: (Boolean) -> Unit) {
        nightModeLiveData.observe(lifecycleOwner, Observer(block))
    }

    override fun observeTextSize(lifecycleOwner: LifecycleOwner, block: (String) -> Unit) {
        textSizeLiveData.observe(lifecycleOwner, Observer(block))
    }

    override fun observeStoredIssueNumber(lifecycleOwner: LifecycleOwner, block: (String) -> Unit) {
        storedIssueNumberLiveData.observe(lifecycleOwner, Observer(block))
    }

    override fun setStoredIssueNumber(number: Int) {
        storedIssueNumberLiveData.postValue(number.toString())
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

    override fun initializeSettings(applicationContext: Context) {
        applicationContext.getSharedPreferences(PREFERENCES_TAZAPICSS, Context.MODE_PRIVATE)?.let {

            textSizeLiveData =
                SharedPreferenceStringLiveData(it, "text_font_size", "100")
            nightModeLiveData =
                SharedPreferenceBooleanLiveData(it, "text_night_mode", false)
        }

        applicationContext.getSharedPreferences(PREFERENCES_GENERAL, Context.MODE_PRIVATE)?.let {
            storedIssueNumberLiveData = SharedPreferenceStringLiveData(
                it,
                "general_keep_number_issues",
                KEEP_ISSUES_DOWNLOADED_DEFAULT.toString()
            )

        }
    }
}