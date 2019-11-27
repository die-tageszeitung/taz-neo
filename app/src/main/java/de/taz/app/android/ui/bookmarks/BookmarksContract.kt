package de.taz.app.android.ui.bookmarks

import android.view.MenuItem
import de.taz.app.android.api.models.Article
import de.taz.app.android.base.BaseContract

interface BookmarksContract {

    interface View : BaseContract.View {

        fun setBookmarks(bookmarks: List<Article>)

        fun shareArticle(article: Article)
    }

    interface Presenter : BaseContract.Presenter {

        fun debookmarkArticle(article: Article)

        fun onBackPressed(): Boolean

        fun onBottomNavigationItemClicked(menuItem: MenuItem)

        fun openArticle(article: Article)

        fun shareArticle(article: Article)
    }

    interface DataController : BaseContract.DataController

}