package de.taz.app.android.ui.bookmarks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import de.taz.app.android.persistence.repository.ArticleRepository

class BookmarksViewModel(application: Application) : AndroidViewModel(application) {
    val bookmarkedArticles = ArticleRepository.getInstance(getApplication()).getBookmarkedArticles()
}
