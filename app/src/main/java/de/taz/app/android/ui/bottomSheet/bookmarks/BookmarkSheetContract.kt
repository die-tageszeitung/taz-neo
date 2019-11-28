package de.taz.app.android.ui.bottomSheet.bookmarks

import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.base.BaseContract

interface BookmarkSheetContract {

    interface View: BaseContract.View {
        fun setIsBookmarked(bookmarked: Boolean)

        fun getArticleFileName(): String
    }

    interface Presenter: BaseContract.Presenter {
        fun openBookmarks()

        fun toggleBookmark()
    }

    interface DataController: BaseContract.DataController {
        fun getArticleStub(): ArticleStub?

        fun setArticleStub(articleFileName: String)

        fun observeIsBookmarked(lifecycleOwner: LifecycleOwner, block: (Boolean) -> Unit)
    }

}