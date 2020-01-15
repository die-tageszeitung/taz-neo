package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.observe
import de.taz.app.android.api.models.Article
import de.taz.app.android.base.BasePresenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ArticlePagerPresenter : BasePresenter<ArticlePagerContract.View, ArticlePagerDataController>(
    ArticlePagerDataController::class.java
), ArticlePagerContract.Presenter {

    override fun onViewCreated(savedInstanceState: Bundle?) {
        val localView = getView()
        val localViewModel = viewModel
        if (localViewModel != null && localView != null) {
            localViewModel.getArticleList(
                localViewModel!!.bookmarksArticle).observe(localView.getLifecycleOwner()
            ) { articles ->
                if (articles.isNotEmpty()) {
                    localView.setArticles(articles, localViewModel.getCurrentPosition())
                }
            }
            localViewModel.observeCurrentPosition(localView.getLifecycleOwner()) { position ->
                localView.persistPosition(position)
            }
        }
    }

    override fun setInitialArticle(article: Article, bookmarksArticle: Boolean) {
        viewModel?.setInitialArticle(article, bookmarksArticle)
    }

    override fun getCurrentPosition(): Int? {
        return viewModel?.getCurrentPosition()
    }

    override fun setCurrentPosition(position: Int) {
        viewModel?.setCurrentPosition(position)
    }

    override fun onBackPressed(): Boolean {
        val localView = getView()
        val localViewModel = viewModel
        if (localView != null && localViewModel != null && !localViewModel.bookmarksArticle) {
            localView.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                localViewModel.getCurrentSection()?.also {
                    localView.getMainView()?.showInWebView(it)
                } ?: run {
                    localViewModel.getArticleList(localViewModel!!.bookmarksArticle).value?.get(
                        localViewModel.getCurrentPosition()
                    )?.getIssue()?.sectionList?.first()?.let {
                        localView.getMainView()?.showInWebView(it)
                    }
                }
            }
            return true
        } else {
            return false
        }
    }

}