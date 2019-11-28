package de.taz.app.android.ui.bottomSheet.bookmarks

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.ui.bookmarks.BookmarksFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BookmarkSheetPresenter(
    private val articleRepository: ArticleRepository = ArticleRepository.getInstance()
) : BasePresenter<BookmarkSheetContract.View, BookmarkSheetDataController>(
    BookmarkSheetDataController::class.java
), BookmarkSheetContract.Presenter {

    override fun attach(view: BookmarkSheetContract.View) {
        super.attach(view)
        viewModel?.setArticleStub(view.getArticleFileName())
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        getView()?.getLifecycleOwner()?.let {
            viewModel?.observeIsBookmarked(it) { isBookmarked ->
                getView()?.setIsBookmarked(isBookmarked)
            }
        }
    }

    override fun openBookmarks() {
        getView()?.getMainView()?.showMainFragment(BookmarksFragment())
    }

    override fun toggleBookmark() {
        val articleStub = viewModel?.getArticleStub()
        articleStub?.let {
            getView()?.getLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                if (articleStub.bookmarked) {
                    articleRepository.debookmarkArticle(articleStub)
                } else {
                    articleRepository.bookmarkArticle(articleStub)
                }
            }
        }
    }

}