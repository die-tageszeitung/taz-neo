package de.taz.app.android.ui.bookmarks

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.webview.pager.BookmarkPagerViewModel
import kotlinx.android.synthetic.main.fragment_bookmarks.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class BookmarkListFragment :
    BaseMainFragment(R.layout.fragment_bookmarks) {

    private var recycleAdapter: BookmarkListAdapter? = null
    private var articleRepository: ArticleRepository? = null

    private val bookmarkPagerViewModel: BookmarkPagerViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(context.applicationContext)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        drawer_menu_list.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this.context)
            adapter = recycleAdapter
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recycleAdapter = recycleAdapter ?: BookmarkListAdapter(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bookmarkPagerViewModel.bookmarkedArticlesLiveData.observe(viewLifecycleOwner) { bookmarks ->
            recycleAdapter?.setData((bookmarks ?: emptyList()).toMutableList())
        }

        view.findViewById<TextView>(R.id.fragment_header_default_title)?.apply {
            text = context.getString(
                R.string.fragment_bookmarks_title
            ).toLowerCase(Locale.getDefault())
        }
    }

    fun shareArticle(articleFileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val article = articleRepository?.getStub(articleFileName)
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

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                Intent().apply {
                    Intent(requireActivity(), MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(this)
                    }
                }
            }
        }
    }
}