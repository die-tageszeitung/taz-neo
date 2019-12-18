package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.models.Article
import de.taz.app.android.base.BaseDataController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArticlePagerDataController : BaseDataController(),
    ArticlePagerContract.DataController {
    override var currentPosition = 0
    private val articleList = MutableLiveData<List<Article>>(emptyList())

    override fun setInitialArticle(article: Article) {
        viewModelScope.launch(Dispatchers.IO) {
            article.getSection()?.also {
                val issue = it.getIssue()
                val articleList = issue.getArticleList()
                setArticleListAndPosition(articleList, articleList.indexOf(article))
            } ?: run {
                setArticleListAndPosition(listOf(article), 0)
            }
        }
    }

    private fun setArticleListAndPosition(articles: List<Article>, position: Int) {
        currentPosition = if (position >= 0) position else 0
        articleList.postValue(articles)
    }

    override fun getArticleList(): LiveData<List<Article>> = articleList
}
