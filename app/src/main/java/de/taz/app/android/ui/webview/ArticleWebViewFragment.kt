package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.view.MenuItem
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import kotlinx.android.synthetic.main.fragment_webview.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ArticleWebViewFragment(val article: Article? = null) : WebViewFragment(), AppWebViewCallback {

    override val menuId: Int = R.menu.navigation_bottom_article
    override val headerId: Int = R.layout.fragment_webview_header_article

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        CoroutineScope(Dispatchers.IO).launch {
            activity?.let {activity ->
                article?.let {article ->
                    article.getSection()?.let { section ->
                        val file = File(
                            ContextCompat.getExternalFilesDirs(
                                requireActivity().applicationContext, null
                            ).first(),
                            "${section.issueBase.tag}/${article.articleFileName}"
                        )
                        lifecycleScope.launch { fileLiveData.value = file }
                        activity.runOnUiThread {
                            view.findViewById<TextView>(R.id.section).apply {
                                text = section.title
                            }
                            view.findViewById<TextView>(R.id.article_num).apply {
                                text = "x/${section.articleList.size}"
                            }
                        }
                    }
                }
            }
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onBottomNavigationItemSelected(menuItem: MenuItem) {
        when(menuItem.itemId) {
            R.id.bottom_navigation_action_bookmark -> {
                article?.let {
                    activity?.let { activity ->
                        if (article.bookmarked) {
                            val bookmark = navigation_bottom.menu.findItem(
                                R.id.bottom_navigation_action_bookmark
                            )
                            bookmark.icon = activity.getDrawable(R.drawable.ic_bookmark_red)
                        }
                    }
                }
            }
        }
    }
}

