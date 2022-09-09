package de.taz.app.android.ui.bottomSheet.textSettings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import de.taz.app.android.MAX_TEST_SIZE
import de.taz.app.android.MIN_TEXT_SIZE
import de.taz.app.android.dataStore.TazApiCssDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class TextSettingsViewModel(application: Application) : AndroidViewModel(application),
    CoroutineScope {

    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(application)

    // Why is that a string TODO: migrate it to integer
    private val textSizeLiveData = tazApiCssDataStore.fontSize.asLiveData()

    private val nightModeLiveData = tazApiCssDataStore.nightMode.asLiveData()


    fun observeNightMode(lifecycleOwner: LifecycleOwner, block: (Boolean) -> Unit) {
        nightModeLiveData.observe(lifecycleOwner, Observer(block))
    }

    fun observeFontSize(lifecycleOwner: LifecycleOwner, block: (String) -> Unit) {
        textSizeLiveData.observe(lifecycleOwner, Observer(block))
    }

    fun setNightMode(activated: Boolean) {
        viewModelScope.launch { tazApiCssDataStore.nightMode.set(activated) }
    }

    fun resetFontSize() {
        viewModelScope.launch { tazApiCssDataStore.fontSize.reset() }
    }

    fun decreaseFontSize() {
        viewModelScope.launch {
            val newSize = getFontSize() - 10
            if (newSize >= MIN_TEXT_SIZE) {
                setFontSize(newSize.toString())
            }
        }
    }

    fun increaseFontSize() {
        viewModelScope.launch {
            val newSize = getFontSize() + 10
            if (newSize <= MAX_TEST_SIZE) {
                setFontSize(newSize.toString())
            }
        }
    }

    private fun setFontSize(value: String) {
        viewModelScope.launch { tazApiCssDataStore.fontSize.set(value) }
    }

    private suspend fun getFontSize(): Int = tazApiCssDataStore.fontSize.get().toInt()

    override val coroutineContext: CoroutineContext = SupervisorJob()
}