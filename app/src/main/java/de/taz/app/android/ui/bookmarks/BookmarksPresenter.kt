package de.taz.app.android.ui.bookmarks

import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.ui.feed.FeedFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BookmarksPresenter : BasePresenter<BookmarksContract.View, BookmarksDataController>
    (BookmarksDataController::class.java), BookmarksContract.Presenter {

    override fun onViewCreated(savedInstanceState: Bundle?) {
        getView()?.let { view ->
            viewModel?.bookmarkedArticles?.observe(view.getLifecycleOwner(), Observer { bookmarks ->
                view.setBookmarks(bookmarks ?: emptyList())
            })
        }
    }

    override fun openArticle(article: Article) {
        getView()?.getMainView()?.showInWebView(article)
    }

    override fun debookmarkArticle(article: Article) {
        getView()?.getLifecycleOwner().let {
            it?.lifecycleScope?.launch(Dispatchers.IO) {
                ArticleRepository.getInstance().debookmarkArticle(article)
            }
        }
    }

    override fun onBackPressed(): Boolean {
        getView()?.getMainView()?.showMainFragment(FeedFragment())
        return true
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home ->
                getView()?.getMainView()?.showMainFragment(FeedFragment())
        }
    }

    override fun shareArticle(article: Article) {
        getView()?.let { view ->
            view.shareArticle(article)
        }
    }
}
