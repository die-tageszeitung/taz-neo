package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.*
import de.taz.app.android.api.models.Article
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val DEFAULT_POSITION = 0

class ArticlePagerDataController : BaseDataController(),
    ArticlePagerContract.DataController {

    private val currentPosition = MutableLiveData<Int>().apply { postValue(DEFAULT_POSITION) }

    private val log by Log

    private val articleList = MutableLiveData<List<Article>>(emptyList())

    override fun setInitialArticle(article: Article) {
        viewModelScope.launch(Dispatchers.IO) {
            val issue = article.getIssue()
            val articleList = issue?.getArticleList()
            withContext(Dispatchers.Main) {
                articleList?.let {
                    setArticleListAndPosition(articleList)
                    setPosition(articleList.indexOf(article))
                } ?: run {
                    setArticleListAndPosition(listOf(article))
                    setPosition(0)
                }
            }
        }
    }

    override fun setCurrentPosition(position: Int) {
        currentPosition.postValue(position)
    }

    override fun getCurrentPosition(): Int {
        return currentPosition.value ?: DEFAULT_POSITION
    }

    override fun observeCurrentPosition(viewLifecycleOwner: LifecycleOwner, block: (Int) -> Unit) {
        currentPosition.observe(viewLifecycleOwner, Observer(block))
    }

    private fun setPosition(position: Int) {
        log.debug("setPosition $position")
        setCurrentPosition(if (position >= 0) position else DEFAULT_POSITION)
    }

    private fun setArticleListAndPosition(articles: List<Article>) {
        articleList.postValue(articles)
    }

    override suspend fun getCurrentSection() = withContext(Dispatchers.IO) {
        articleList.value?.get(getCurrentPosition())?.getSection()
    }

    override fun getArticleList(): LiveData<List<Article>> = articleList
}
