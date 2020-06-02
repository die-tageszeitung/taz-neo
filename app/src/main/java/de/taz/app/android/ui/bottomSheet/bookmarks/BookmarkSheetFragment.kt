package de.taz.app.android.ui.bottomSheet.bookmarks

import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import de.taz.app.android.R
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.base.ViewModelFragment
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.ui.bookmarks.BookmarksFragment
import de.taz.app.android.ui.main.MainActivity
import kotlinx.android.synthetic.main.fragment_bottom_sheet_bookmarks.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BookmarkSheetFragment :
    ViewModelFragment<BookmarkSheetViewModel>(R.layout.fragment_bottom_sheet_bookmarks) {

    private val articleRepository = ArticleRepository.getInstance(activity?.applicationContext)

    companion object {
        fun create(articleFileName: String): BookmarkSheetFragment {
            val fragment = BookmarkSheetFragment()
            fragment.setArticleFileName(articleFileName)
            return fragment
        }
    }

    fun setArticleFileName(articleFileName: String) {
        viewModel.articleFileName = articleFileName
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_bottom_sheet_bookmarks_add?.setOnClickListener {
            toggleBookmark()
            (this.parentFragment as? DialogFragment)?.dismiss()
        }

        fragment_bottom_sheet_bookmarks_my_bookmarks?.setOnClickListener {
            (activity as? MainActivity)?.showMainFragment(BookmarksFragment())
            (this.parentFragment as? DialogFragment)?.dismiss()
        }

        viewModel.isBookmarkedLiveData.observeDistinct(this) { isBookmarked ->
            fragment_bottom_sheet_bookmarks_add?.text = getText(
                if (isBookmarked)
                    R.string.fragment_bottom_sheet_bookmarks_remove_bookmark
                else
                    R.string.fragment_bottom_sheet_bookmarks_add_bookmark
            )
        }
    }

    private fun toggleBookmark() {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.articleStub?.let { articleStub: ArticleStub ->
                if (articleStub.bookmarked) {
                    articleRepository.debookmarkArticle(articleStub)
                } else {
                    articleRepository.bookmarkArticle(articleStub)
                }
            }
        }
    }
}