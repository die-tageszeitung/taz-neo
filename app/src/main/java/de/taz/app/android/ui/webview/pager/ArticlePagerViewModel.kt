package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.*
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArticlePagerViewModel : ViewModel() {

    val articleNameLiveData = MutableLiveData<String?>(null)
    val articleName: String?
        get() = articleNameLiveData.value

    val showBookmarksLiveData = MutableLiveData(false)
    val showBookmarks: Boolean
        get() = showBookmarksLiveData.value ?: false


    val currentPositionLiveData = MutableLiveData(0)
    val currentPosition
        get() = currentPositionLiveData.value ?: 0

    val articleList
        get() = articleListLiveData.value
    val articleListLiveData = MediatorLiveData<List<ArticleStub>>().apply {
        addSource(showBookmarksLiveData) {
            if (it) {
                articleName?.let {
                    getBookmarkedArticles()
                }
            } else {
                getIssueArticleList()
            }
        }
        addSource(articleNameLiveData) {
            it?.let {
                if (showBookmarks) {
                    getBookmarkedArticles()
                } else {
                    getIssueArticleList()
                }
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

    private fun getIssueArticleList() {
        articleListLiveData.apply {
            articleName?.let { articleName ->
                CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO).launch {
                    val articles = ArticleRepository.getInstance()
                        .getIssueArticleStubListByArticleName(articleName)
                    sectionNameListLiveData.postValue(articles.map { it.getSectionStub()?.key })
                    // only set position of article if no position has been restored
                    if (currentPosition <= 0) {
                        currentPositionLiveData.postValue(
                            articles.indexOfFirst { it.key == articleName }
                        )
                    }
                    postValue(articles)
                }
            }
        }
    }

}