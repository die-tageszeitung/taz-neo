package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BaseDataController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArticlePagerDataController : BaseDataController(),
    ArticlePagerContract.DataController {
    override var currentPosition = 0
    private val articleList =
        MutableLiveData<List<Article>>(emptyList())
    private var section: Section? = null

    override fun setInitialArticle(article: Article) {
        articleList.postValue(listOf(article))

        viewModelScope.launch(Dispatchers.IO) {
            section = article.getSection()?.also {
                setArticleListAndPosition(it.articleList, it.articleList.indexOf(article))
            }
        }
    }

    private fun setArticleListAndPosition(articles: List<Article>, position: Int) {
        currentPosition = if (position >= 0) position else 0
        articleList.postValue(articles)
    }

    override fun getSection() = section
    override fun getArticleList(): LiveData<List<Article>> = articleList
}
