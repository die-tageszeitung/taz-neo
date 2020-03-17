package de.taz.app.android.ui.webview

import androidx.lifecycle.liveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers

class ArticleWebViewViewModel : WebViewViewModel<Article>() {

    override val displayableLiveData = displayableKeyLiveData.switchMap { displayableKey ->
        liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
            displayableKey?.let { displayableKey ->
                emitSource(ArticleRepository.getInstance().getLiveData(displayableKey))
            } ?: emit(null)
        }
    }

}