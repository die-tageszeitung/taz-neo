package de.taz.app.android.ui.bottomSheet.bookmarks

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import de.taz.app.android.R
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.databinding.FragmentBottomSheetBookmarksBinding
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.ui.bookmarks.BookmarkListActivity
import de.taz.app.android.ui.search.SearchResultPagerFragment
import kotlinx.android.synthetic.main.fragment_bottom_sheet_bookmarks.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class BookmarkSheetFragment :
    BaseViewModelFragment<BookmarkSheetViewModel, FragmentBottomSheetBookmarksBinding>() {

    private var articleRepository: ArticleRepository? = null
    private var articleFileName: String? = null
    private var datePublished: Date? = null
    private var pagerFragment: SearchResultPagerFragment? = null

    companion object {
        fun create(
            articleFileName: String,
            datePublished: Date? = null,
            pagerFragment: SearchResultPagerFragment? = null
        ): BookmarkSheetFragment {
            val fragment = BookmarkSheetFragment()
            fragment.articleFileName = articleFileName
            fragment.datePublished = datePublished
            fragment.pagerFragment = pagerFragment
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setArticleFileName()

        fragment_bottom_sheet_bookmarks_add?.setOnClickListener {
            toggleBookmark()
            (this.parentFragment as? DialogFragment)?.dismiss()
        }

        fragment_bottom_sheet_bookmarks_my_bookmarks?.setOnClickListener {
            Intent(requireActivity(), BookmarkListActivity::class.java).apply {
                startActivity(this)
            }
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

    private fun setArticleFileName() {
        viewModel.articleFileName = this.articleFileName
    }

    private fun toggleBookmark() {
        CoroutineScope(Dispatchers.IO).launch {
            viewModel.articleStub?.let { articleStub: ArticleStub ->
                if (articleStub.bookmarked) {
                    articleRepository?.debookmarkArticle(articleStub)
                } else {
                    articleRepository?.bookmarkArticle(articleStub)
                }
            } ?: viewModel.articleFileName?.let {
                // no articleStub so probably article not downloaded, so download it:
                datePublished?.let { date ->
                    // We can assume that we want to bookmark it as we cannot debookmark a not downloaded article
                    withContext(Dispatchers.Main) {
                        pagerFragment?.setIcon(R.id.bottom_navigation_action_bookmark, R.drawable.ic_bookmark_filled)
                    }
                    viewModel.downloadArticleAndSetBookmark(it, date)
                }
            }
        }
    }
}