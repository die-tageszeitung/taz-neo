package de.taz.app.android.ui.webview

import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.models.Section
import de.taz.app.android.persistence.repository.SectionRepository
import kotlinx.coroutines.Dispatchers

class SectionWebViewViewModel : WebViewViewModel<Section>() {
    override val displayableLiveData = displayableKeyLiveData.switchMap { displayableKey ->
        liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
            displayableKey?.let { displayableKey ->
                emitSource(SectionRepository.getInstance().getLiveData(displayableKey))
            } ?: emit(null)
        }
    }
}