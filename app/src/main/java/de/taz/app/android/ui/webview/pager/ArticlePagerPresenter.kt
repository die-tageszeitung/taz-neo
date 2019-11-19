package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// FIXME: not in contract yet
class ArticlePagerDataController : BaseDataController(),
    ArticlePagerContract.DataController {
    var currentPosition = 0
    val articleList = MutableLiveData<List<Article>>(emptyList())
    var section: Section? = null

    fun setInitialArticle(article: Article) {
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
}

class ArticlePagerPresenter : BasePresenter<ArticlePagerContract.View, ArticlePagerDataController>(
    ArticlePagerDataController::class.java
), ArticlePagerContract.Presenter {

    val log by Log

    override fun onViewCreated(savedInstanceState: Bundle?) {
        val localView = getView()
        val localViewModel = viewModel
        if (localViewModel != null && localView != null) {
            localViewModel.articleList.observe(localView.getLifecycleOwner()) { articles ->
                log.debug("setArticles: ${articles.size} $localViewModel.currentPosition")
                localView.setArticles(articles, localViewModel.currentPosition)
            }

        }
    }

    override fun setInitialArticle(article: Article) {
        viewModel?.setInitialArticle(article)
    }

    override fun setCurrrentPosition(position: Int) {
        viewModel?.currentPosition = position
        log.debug("currentPosition = $position")
    }

    override fun onBackPressed() {
        val localView = getView()
        val localViewModel = viewModel
        if (localView != null && localViewModel != null) {
            localViewModel.section?.also {
                localView.getMainView()?.showInWebView(it)
            }
        }
    }

}