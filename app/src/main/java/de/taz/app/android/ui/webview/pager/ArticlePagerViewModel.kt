package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.*
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArticlePagerViewModel : ViewModel() {

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
                getIssueArticleList()
            }
        }
    }

    var sectionNameListLiveData = MutableLiveData<List<String?>>(emptyList())

    var issueOperationsLiveData = MutableLiveData<IssueOperations?>(null)

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
                    issueOperationsLiveData.postValue(articles.first().getIssueOperations())
                }
            }
        }
    }

}