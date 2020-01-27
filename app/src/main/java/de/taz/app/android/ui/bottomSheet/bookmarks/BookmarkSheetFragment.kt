package de.taz.app.android.ui.bottomSheet.bookmarks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import de.taz.app.android.R
import de.taz.app.android.base.BaseFragment
import kotlinx.android.synthetic.main.fragment_bottom_sheet_bookmarks.*

class BookmarkSheetFragment(private val articleFileName: String) :
    BaseFragment<BookmarkSheetPresenter>(),
    BookmarkSheetContract.View {

    override val presenter = BookmarkSheetPresenter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_bottom_sheet_bookmarks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)

        fragment_bottom_sheet_bookmarks_add?.setOnClickListener {
            presenter.toggleBookmark()
            (this.parentFragment as DialogFragment).dismiss()
        }

        fragment_bottom_sheet_bookmarks_my_bookmarks?.setOnClickListener {
            presenter.openBookmarks()
        }
    }

    override fun setIsBookmarked(bookmarked: Boolean) {
        fragment_bottom_sheet_bookmarks_add?.text = getText(
            if (bookmarked)
                R.string.fragment_bottom_sheet_bookmarks_remove_bookmark
            else
                R.string.fragment_bottom_sheet_bookmarks_add_bookmark
        )
    }

    override fun getArticleFileName(): String {
        return articleFileName
    }
}