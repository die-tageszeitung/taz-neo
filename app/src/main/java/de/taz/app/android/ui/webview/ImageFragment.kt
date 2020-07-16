package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.api.models.Image
import de.taz.app.android.monkey.observeDistinctUntil
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.runIfNotNull

const val HTML_BACKGROUND_CONTAINER = """
<html>
    <body style="background: transparent;margin:0;">
        <div style="width:100%%; height:100%%; display: flex; align-items: center;">
            <img src="%s" style="width:100%%; "/>
        </div>
    </body>
</html>
"""

class ImageFragment : Fragment(R.layout.fragment_image) {
    var image: Image? = null

    fun newInstance(image: Image?): ImageFragment {
        val fragment = ImageFragment()
        fragment.image = image
        return fragment
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        val webView = view?.findViewById<WebView>(R.id.image_view)
        image?.download(context?.applicationContext)
        image?.isDownloadedLiveData(context?.applicationContext)?.observeDistinctUntil(
            this, { isDownloaded ->
                showImageIfDownloaded(isDownloaded)
            }, { isDownloaded ->
                isDownloaded
            })

        webView?.apply {
            webChromeClient = AppWebChromeClient {
                view.findViewById<View>(R.id.loading_screen).visibility = View.GONE
            }
            settings?.apply {
                allowFileAccess = true
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                domStorageEnabled = true
            }
        }

        return view
    }

    private fun showImageIfDownloaded(isDownloaded: Boolean) {
        if (isDownloaded) {
            val webView = view?.findViewById<WebView>(R.id.image_view)
            val fileHelper = FileHelper.getInstance(context)
            runIfNotNull(image, context, webView) { image, context, web ->
                fileHelper.getFileDirectoryUrl(context).let { fileDir ->
                    val uri = "${image.folder}/${image.name}"
                    web.loadDataWithBaseURL(
                        fileDir,
                        HTML_BACKGROUND_CONTAINER.format("$fileDir/$uri"),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
        }
    }
}