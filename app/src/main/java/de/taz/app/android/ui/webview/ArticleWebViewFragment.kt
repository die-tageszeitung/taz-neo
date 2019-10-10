package de.taz.app.android.ui.webview

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

    override fun onResume() {
        CoroutineScope(Dispatchers.IO).launch {
            val file = File(
                ContextCompat.getExternalFilesDirs(
                    requireActivity().applicationContext, null).first(),
               "${article.getSection()!!.issueBase.tag}/${article.articleFileName}"
            )
            lifecycleScope.launch { fileLiveData.value = file}
        }
        super.onResume()
    }
}

