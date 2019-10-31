package de.taz.app.android.ui.drawer.bookmarks

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.models.ArticleStub

class BookmarksViewModel : ViewModel() {

    val bookmarkedArticleStubs = MutableLiveData<List<ArticleStub>?>().apply {
        postValue(null)
    }
}
