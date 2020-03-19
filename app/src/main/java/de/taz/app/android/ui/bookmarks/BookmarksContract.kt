package de.taz.app.android.ui.bookmarks

import android.view.MenuItem
import de.taz.app.android.api.models.Article
import de.taz.app.android.base.BaseContract

interface BookmarksContract {

    interface View : BaseContract.View {

        fun setBookmarks(bookmarks: List<Article>)

        fun shareArticle(articleFileName: String)
    }

    interface Presenter : BaseContract.Presenter {

        fun debookmarkArticle(articleFileName: String)

        fun onBackPressed(): Boolean

        fun onBottomNavigationItemClicked(menuItem: MenuItem)

        fun openArticle(articleFileName: String)

        fun shareArticle(articleFileName: String)
    }

    interface DataController : BaseContract.DataController

}