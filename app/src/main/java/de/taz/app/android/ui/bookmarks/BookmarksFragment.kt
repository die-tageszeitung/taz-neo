package de.taz.app.android.ui.bookmarks

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.persistence.repository.ArticleRepository
import kotlinx.android.synthetic.main.fragment_bookmarks.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class BookmarksFragment :
    BaseMainFragment<BookmarksPresenter>(R.layout.fragment_bookmarks),
    BookmarksContract.View {

    override val presenter: BookmarksPresenter = BookmarksPresenter()

    private val recycleAdapter = BookmarksAdapter(presenter)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        drawer_menu_list.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this.context)
            adapter = recycleAdapter
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)

        view.findViewById<TextView>(R.id.fragment_header_default_title)?.apply {
            text = context.getString(
                R.string.fragment_bookmarks_title
            ).toLowerCase(Locale.getDefault())
        }
    }

    override fun setBookmarks(bookmarks: List<Article>) {
        recycleAdapter.setData(bookmarks.toMutableList())
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        presenter.onBottomNavigationItemClicked(menuItem)
    }

    override fun shareArticle(articleFileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val article = ArticleRepository.getInstance().getStub(articleFileName)
            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, article?.onlineLink)
                type = "text/plain"
            }
            withContext(Dispatchers.Main) {
                val shareIntent = Intent.createChooser(sendIntent, null)
                    startActivity(shareIntent)
            }
        }
    }
}