package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArticlePagerViewModel : ViewModel() {

    val articleNameLiveData = MutableLiveData<String?>(null)
    var articleName: String?
        get() = articleNameLiveData.value
        set(value) = articleNameLiveData.postValue(value)

    val articlePosition: Int
        get() = articleList?.indexOfFirst { it.key == articleName } ?: 0

    val showBookmarksLiveData = MutableLiveData(false)
    var showBookmarks: Boolean
        get() = showBookmarksLiveData.value ?: false
        set(value) = showBookmarksLiveData.postValue(value)


    val currentPositionLiveData = MutableLiveData(0)
    var currentPosition
        get() = currentPositionLiveData.value
        set(value) {
            currentPositionLiveData.value = value
        }

    val articleList
        get() = articleListLiveData.value
    val articleListLiveData = MediatorLiveData<List<ArticleStub>>().apply {
        addSource(showBookmarksLiveData) {
            if(it) {
                articleName?.let {
                    getBookmarkedArticles()
                }
            } else {
                getIssueArticleList()
            }
        }
        addSource(articleNameLiveData) {
            it?.let {
                if(showBookmarks) {
                    getBookmarkedArticles()
                } else {
                    getIssueArticleList()
                }
            }
        }
    }

    private fun getBookmarkedArticles() {
        articleListLiveData.apply {
            CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO).launch {
                val bookmarkedArticles =
                    ArticleRepository.getInstance().getBookmarkedArticleStubList()
                postValue(bookmarkedArticles)
            }
        }
    }

    private fun getIssueArticleList() {
        articleListLiveData.apply {
            articleName?.let {
                CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO).launch {
                    val articleList =
                        ArticleRepository.getInstance().getIssueArticleStubListByArticleName(it)
                    postValue(articleList)
                }
            }
        }
    }

}