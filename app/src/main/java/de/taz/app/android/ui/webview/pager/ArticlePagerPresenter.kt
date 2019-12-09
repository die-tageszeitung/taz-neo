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
            localViewModel.getArticleList().observe(localView.getLifecycleOwner()) { articles ->
                if (articles.isNotEmpty()) {
                    localView.setArticles(articles, localViewModel.currentPosition)
                }
            }

        }
    }

    override fun setInitialArticle(article: Article) {
        viewModel?.setInitialArticle(article)
    }

    override fun setCurrrentPosition(position: Int) {
        viewModel?.currentPosition = position
    }

    override fun onBackPressed() {
        val localView = getView()
        val localViewModel = viewModel
        if (localView != null && localViewModel != null) {
            localView.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                localViewModel.getCurrentSection()?.also {
                    localView.getMainView()?.showInWebView(it)
                } ?: run {
                    localViewModel.getArticleList().value?.get(
                        localViewModel.currentPosition
                    )?.getIssue()?.sectionList?.first()?.let {
                        localView.getMainView()?.showInWebView(it)
                    }
                }
            }
        }
    }

}