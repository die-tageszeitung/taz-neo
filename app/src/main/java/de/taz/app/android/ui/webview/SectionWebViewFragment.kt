package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.*
import android.webkit.*
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.api.models.Section
import de.taz.app.android.util.FileHelper
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class SectionWebViewFragment(val section: Section) : WebViewFragment(), ArticleWebViewCallback {

    private val log by Log

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview, container, false)
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onResume() {
        super.onResume()
        web_view.webViewClient = TazWebViewClient()
        web_view.webChromeClient = WebChromeClient()
        web_view.settings.javaScriptEnabled = true
        web_view.setArticleWebViewCallback(this)



        context?.let {
            web_view.addJavascriptInterface(TazApiJs(), "ANDROIDAPI")
            CoroutineScope(Dispatchers.IO).launch {
                val file = File(
                    ContextCompat.getExternalFilesDirs(it.applicationContext, null).first(),
                    "${section.issueBase.tag}/${section.sectionHtml.name}"
                )
                activity?.runOnUiThread { web_view.loadUrl("file://${file.absolutePath}") }
            }
        }

        // handle clicks of the back button
        web_view.setOnKeyListener(object: View.OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent?): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == MotionEvent.ACTION_UP && web_view.canGoBack()) {
                    web_view.goBack()
                    return true
                }
                return false
            }
        })
    }

}
