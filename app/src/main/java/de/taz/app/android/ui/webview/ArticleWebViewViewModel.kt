package de.taz.app.android.ui.webview

import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.models.ArticleStub
import kotlinx.coroutines.Dispatchers

class ArticleWebViewViewModel : WebViewViewModel<ArticleStub>() {

    val isBookmarkedLiveData = liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
        displayable?.let { emitSource(it.isBookmarkedLiveData()) } ?: emit(false)
    }

}