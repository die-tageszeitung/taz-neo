package de.taz.app.android.ui.bottomSheet.textSettings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
    val textSizeFlow = tazApiCssDataStore.fontSize.asFlow()

    val nightModeFlow = tazApiCssDataStore.nightMode.asFlow()

    val multiColumnModeFlow = tazApiCssDataStore.multiColumnMode.asFlow()

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

    fun setMultiColumnMode(value: Boolean) {
        launch {
            tazApiCssDataStore.multiColumnMode.set(value)
        }
    }

    private suspend fun setFontSize(value: String) {
        tazApiCssDataStore.fontSize.set(value)
    }

    private suspend fun getFontSize(): Int = tazApiCssDataStore.fontSize.get().toInt()

    override val coroutineContext: CoroutineContext = SupervisorJob()
}