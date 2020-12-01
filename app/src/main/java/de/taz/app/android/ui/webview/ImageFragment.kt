package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_JQUERY_FILE
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.RESOURCE_FOLDER
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.monkey.getColorFromAttr
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val HTML_BACKGROUND_CONTAINER = """
<html>
<head>
    <script src="%s"></script>
</head>
    <body style="background: transparent;margin:0;">
        <div style="width:100%%; height:100%%; display: flex; align-items: center;">
            <img id="image" src="%s" style="width:100%%; height: 100%%;  object-fit: contain;"/>
        </div>
    </body>
</html>
"""

class ImageFragment : Fragment(R.layout.fragment_image) {
    var image: Image? = null
    private var toDownloadImage: Image? = null
    private lateinit var dataService: DataService
    private lateinit var issueRepository: IssueRepository
    private lateinit var fileHelper: FileHelper

    val log by Log

    companion object {
        fun createInstance(
            image: Image?,
            toDownloadImage: Image?
        ): ImageFragment {
            val fragment = ImageFragment()
            fragment.image = image
            fragment.toDownloadImage = toDownloadImage
            return fragment
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dataService = DataService.getInstance(requireContext().applicationContext)
        issueRepository = IssueRepository.getInstance(requireContext().applicationContext)
        fileHelper = FileHelper.getInstance(requireContext().applicationContext)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        val webView = view?.findViewById<WebView>(R.id.image_view)
        webView?.apply {
            image?.let {
                showImageInWebView(it, webView)
            }
            toDownloadImage?.let {
                lifecycleScope.launch(Dispatchers.IO) {
                    dataService.ensureDownloaded(
                        FileEntry(it),
                        issueRepository.getIssueStubForImage(it).baseUrl
                    )
                    withContext(Dispatchers.Main) {
                        fadeInImageInWebView(it, webView)
                    }
                }
            }

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
                javaScriptEnabled = true
            }
            context?.applicationContext?.getColorFromAttr(R.color.backgroundColor)?.let {
                setBackgroundColor(
                    it
                )
            }
        }

        return view
    }

    private fun showImageInWebView(toShowImage: Image, webView: WebView) {
        val fileHelper = FileHelper.getInstance(context)
        val jqueryFile =
            "file://${fileHelper.getFileByPath("$RESOURCE_FOLDER/$WEBVIEW_JQUERY_FILE").path}"
        runIfNotNull(toShowImage, context, webView) { image, context, web ->
            RESOURCE_FOLDER
            fileHelper.getFileDirectoryUrl(context).let { fileDir ->
                val uri = "${image.folder}/${image.name}"
                web.loadDataWithBaseURL(
                    fileDir,
                    HTML_BACKGROUND_CONTAINER.format(jqueryFile, "$fileDir/$uri"),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    }

    private fun fadeInImageInWebView(toShowImage: Image, webView: WebView) {
        runIfNotNull(toShowImage, context, webView) { image, context, web ->
            fileHelper.getFileDirectoryUrl(context).let { fileDir ->
                val uri = "${image.folder}/${image.name}"
                if (web.url != null) {
                    web.evaluateJavascript(
                        """
                        document.getElementById("image").src="$fileDir/$uri";
                    """.trimIndent()
                    ) { log.debug("${image.name} replaced") }
                } else {
                    showImageInWebView(toShowImage, webView)
                }
            }
        }
    }
}