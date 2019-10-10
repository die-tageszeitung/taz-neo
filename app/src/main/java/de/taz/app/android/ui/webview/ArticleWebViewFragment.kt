package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class ArticleWebViewFragment(val article: Article) : WebViewFragment(), AppWebViewCallback {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview_article, container, false)
    }

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

