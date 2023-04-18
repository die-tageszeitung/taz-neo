package de.taz.app.android.ui.bottomSheet.textSettings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.distinctUntilChanged
import de.taz.app.android.MAX_TEXT_SIZE
import de.taz.app.android.MIN_TEXT_SIZE
import de.taz.app.android.dataStore.TazApiCssDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

class TextSettingsViewModel(application: Application) : AndroidViewModel(application), CoroutineScope {

    private val tazApiCssDataStore = TazApiCssDataStore.getInstance(application)

    // Why is that a string TODO: migrate it to integer
    private val textSizeLiveData = tazApiCssDataStore.fontSize.asLiveData()

    private val nightModeLiveData = tazApiCssDataStore.nightMode.asLiveData()


    fun observeNightMode(lifecycleOwner: LifecycleOwner, block: (Boolean) -> Unit) {
        nightModeLiveData.distinctUntilChanged().observe(lifecycleOwner, Observer(block))
    }

    fun observeFontSize(lifecycleOwner: LifecycleOwner, block: (String) -> Unit) {
        textSizeLiveData.distinctUntilChanged().observe(lifecycleOwner, Observer(block))
    }

    fun setNightMode(activated: Boolean) {
        launch { tazApiCssDataStore.nightMode.set(activated) }
    }

    fun resetFontSize() {
        launch { tazApiCssDataStore.fontSize.reset() }
    }

    fun decreaseFontSize() {
        launch {
            val newSize = getFontSize() - 10
            if (newSize >= MIN_TEXT_SIZE) {
                setFontSize(newSize.toString())
            }
        }
    }

    fun increaseFontSize() {
        launch {
            val newSize = getFontSize() + 10
            if (newSize <= MAX_TEXT_SIZE) {
                setFontSize(newSize.toString())
            }
        }
    }

    private fun setFontSize(value: String) {
        launch { tazApiCssDataStore.fontSize.set(value) }
    }

    private suspend fun getFontSize(): Int = tazApiCssDataStore.fontSize.get().toInt()

    override val coroutineContext: CoroutineContext =  SupervisorJob()
}