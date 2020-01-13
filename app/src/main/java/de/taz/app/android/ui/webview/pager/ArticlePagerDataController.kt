package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.*
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleType
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.ui.bookmarks.BookmarksDataController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val DEFAULT_POSITION = 0

class ArticlePagerDataController : BaseDataController(),
    ArticlePagerContract.DataController {

    private val currentPosition = MutableLiveData<Int>().apply { postValue(DEFAULT_POSITION) }

    private val articleList = MutableLiveData<List<Article>>(emptyList())

    var bookmarksArticle: Boolean = false

    override fun setInitialArticle(article: Article, bookmarksArticle: Boolean) {
        this.bookmarksArticle = bookmarksArticle
        if (articleList.value?.isEmpty() == true) {
            viewModelScope.launch(Dispatchers.IO) {
                val issue = article.getIssue()
                val articleList =  issue?.getArticleList()
                withContext(Dispatchers.Main) {
                    if (article.articleType !== ArticleType.IMPRINT) {
                        articleList?.let {
                            initializePosition(articleList.indexOf(article))
                            setArticleListAndPosition(articleList)
                        } ?: run {
                            initializePosition(0)
                            setArticleListAndPosition(listOf(article))
                        }
                    } else {
                        initializePosition(0)
                        setArticleListAndPosition(listOf(article))
                    }

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

    private fun initializePosition(position: Int) {
        setCurrentPosition(if (position >= 0) position else DEFAULT_POSITION)
    }

    private fun setArticleListAndPosition(articles: List<Article>) {
        articleList.postValue(articles)
    }

    override suspend fun getCurrentSection() = withContext(Dispatchers.IO) {
        articleList.value?.get(getCurrentPosition())?.getSection()
    }

    override fun getArticleList(bookmarksArticle: Boolean): LiveData<List<Article>> {
        val bookmarkList = BookmarksDataController().bookmarkedArticles
        return if (bookmarksArticle) bookmarkList else articleList
    }
}
