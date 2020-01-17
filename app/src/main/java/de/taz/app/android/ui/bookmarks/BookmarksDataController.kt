package de.taz.app.android.ui.bookmarks

import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.ArticleRepository

class BookmarksDataController : BaseDataController() {
    val bookmarkedArticles = ArticleRepository.getInstance().getBookmarkedArticles()
}
