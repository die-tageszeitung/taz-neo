package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ArticleWebViewFragment(val article: Article) : WebViewFragment(), AppWebViewCallback {

    override val menuId: Int = R.menu.navigation_bottom_article
    override val headerId: Int = R.layout.fragment_webview_header_article

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        CoroutineScope(Dispatchers.IO).launch {
            article.getSection()?.let {section ->
                val file = File(
                    ContextCompat.getExternalFilesDirs(
                        requireActivity().applicationContext, null
                    ).first(),
                    "${section.issueBase.tag}/${article.articleFileName}"
                )
                lifecycleScope.launch { fileLiveData.value = file }
                activity?.runOnUiThread {
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

