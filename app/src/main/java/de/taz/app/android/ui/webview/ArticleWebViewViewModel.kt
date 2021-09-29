package de.taz.app.android.ui.webview

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import de.taz.app.android.api.models.Article

class ArticleWebViewViewModel(application: Application, savedStateHandle: SavedStateHandle) : WebViewViewModel<Article>(application, savedStateHandle)
