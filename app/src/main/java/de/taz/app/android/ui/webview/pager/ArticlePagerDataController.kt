package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArticlePagerDataController(
    val articleRepository: ArticleRepository = ArticleRepository.getInstance()
) : BaseDataController(),
    ArticlePagerContract.DataController {
    override var currentPosition = 0
    private val articleList =
        MutableLiveData<List<Article>>(emptyList())

    override fun setInitialArticle(article: Article) {
        viewModelScope.launch(Dispatchers.IO) {
            article.getSection()?.also {
                val issue = it.getIssue()
                val articleList = issue.getArticleList()
                setArticleListAndPosition(articleList, articleList.indexOf(article))
            }
        }
    }

    private fun setArticleListAndPosition(articles: List<Article>, position: Int) {
        currentPosition = if (position >= 0) position else 0
        articleList.postValue(articles)
    }

    override fun getCurrentSection() = articleList.value?.get(currentPosition)?.getSection()
    override fun getArticleList(): LiveData<List<Article>> = articleList
}
