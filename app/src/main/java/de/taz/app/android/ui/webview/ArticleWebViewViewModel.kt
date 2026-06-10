package de.taz.app.android.ui.webview

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.SectionStub
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn

class ArticleWebViewViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    WebViewViewModel<Article>(application, savedStateHandle) {

    val articleFlow: Flow<Article> = displayableFlow.filterNotNull()
    val sectionStubFlow: Flow<SectionStub> = articleFlow
        .mapNotNull {
            it.section
        }
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    // TODO check if necessary or if we can use article info
    val issueStubFlow: Flow<IssueStub> = articleFlow
        .mapNotNull {
            it.getIssueStub(application.applicationContext)
        }
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

}
