package de.taz.app.android.ui.bookmarks

import androidx.lifecycle.ViewModel
import de.taz.app.android.persistence.repository.ArticleRepository

class BookmarksViewModel : ViewModel() {
    val bookmarkedArticles = ArticleRepository.getInstance().getBookmarkedArticles()
}
