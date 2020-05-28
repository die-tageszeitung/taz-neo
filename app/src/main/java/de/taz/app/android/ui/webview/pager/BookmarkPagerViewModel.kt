package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.*
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BookmarkPagerViewModel : ViewModel() {

    val articleNameLiveData = MutableLiveData<String?>(null)
    val articleName: String?
        get() = articleNameLiveData.value

    val currentPositionLiveData = MutableLiveData(0)
    val currentPosition
        get() = currentPositionLiveData.value ?: 0

    val articleList
        get() = articleListLiveData.value ?: emptyList()
    val articleListLiveData = MediatorLiveData<List<ArticleStub>>().apply {
        addSource(articleNameLiveData) {
            it?.let {
                getBookmarkedArticles()
            }
        }
    }

    var sectionNameListLiveData = MutableLiveData<List<String?>>(emptyList())

    private fun getBookmarkedArticles() {
        articleListLiveData.apply {
            CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO).launch {
                val bookmarkedArticles =
                    ArticleRepository.getInstance().getBookmarkedArticleStubList()
                postValue(bookmarkedArticles)
                sectionNameListLiveData.postValue(bookmarkedArticles.map { it.getSectionStub()?.key })
                if (currentPosition <= 0) {
                    currentPositionLiveData.postValue(
                        bookmarkedArticles.indexOfFirst { it.key == articleName }
                    )
                }
            }
        }
    }

}