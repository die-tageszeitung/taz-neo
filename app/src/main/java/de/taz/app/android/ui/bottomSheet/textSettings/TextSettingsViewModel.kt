package de.taz.app.android.ui.bottomSheet.textSettings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import de.taz.app.android.MAX_TEST_SIZE
import de.taz.app.android.MIN_TEXT_SIZE
import de.taz.app.android.dataStore.TazApiCssDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TextSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(application)

    // Why is that a string TODO: migrate it to integer
    private val textSizeLiveData = tazApiCssDataStore.fontSize.asLiveData()

    private val nightModeLiveData = tazApiCssDataStore.nightMode.asLiveData()


    fun observeNightMode(lifecycleOwner: LifecycleOwner, block: (Boolean) -> Unit) {
        nightModeLiveData.observe(lifecycleOwner, Observer(block))
    }

    fun observeTextSize(lifecycleOwner: LifecycleOwner, block: (String) -> Unit) {
        textSizeLiveData.observe(lifecycleOwner, Observer(block))
    }

    fun updateNightMode(activated: Boolean) = CoroutineScope(Dispatchers.IO).launch {
        tazApiCssDataStore.nightMode.update(activated)
    }

    fun resetFontSize() = CoroutineScope(Dispatchers.IO).launch {
        tazApiCssDataStore.fontSize.reset()
    }

    fun decreaseFontSize() = CoroutineScope(Dispatchers.IO).launch {
        val newSize = getFontSize() - 10
        if (newSize >= MIN_TEXT_SIZE) {
            updateFontSize(newSize.toString())
        }
    }

    fun increaseFontSize() = CoroutineScope(Dispatchers.IO).launch {
        val newSize = getFontSize() + 10
        if (newSize <= MAX_TEST_SIZE) {
            updateFontSize(newSize.toString())
        }
    }

    private fun updateFontSize(value: String) = CoroutineScope(Dispatchers.IO).launch {
        tazApiCssDataStore.fontSize.update(value)
    }

    private suspend fun getFontSize(): Int = tazApiCssDataStore.fontSize.current().toInt()
}