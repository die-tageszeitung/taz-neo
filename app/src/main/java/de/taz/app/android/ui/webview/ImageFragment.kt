package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.persistence.repository.ImageRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch

private const val HTML_BACKGROUND_CONTAINER = """
<html>
<head></head>
    <body style="background: transparent;margin:0;">
        <div style="width:100%%; height:100%%; display: flex; align-items: center;">
            <img id="image" src="%s" style="width:100%%; height: 100%%;  object-fit: contain;"/>
        </div>
    </body>
</html>
"""

class ImageFragment : Fragment(R.layout.fragment_image) {
    private var image: Image? = null
    private var toDownloadImage: Image? = null

    private lateinit var issueRepository: IssueRepository
    private lateinit var storageService: StorageService
    private lateinit var contentService: ContentService
    private lateinit var imageRepository: ImageRepository

    val log by Log

    private var isImageShownInWebView = false

    companion object {
        fun newInstance(
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
        issueRepository = IssueRepository.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        contentService = ContentService.getInstance(context.applicationContext)
        imageRepository = ImageRepository.getInstance(context.applicationContext)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<WebView>(R.id.image_view)?.apply {
            webChromeClient = AppWebChromeClient {
                view.findViewById<View>(R.id.loading_screen).visibility = View.GONE
            }
            settings.apply {
                allowFileAccess = true
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
                domStorageEnabled = true
                javaScriptEnabled = true
            }
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.backgroundColor))
            WebView.setWebContentsDebuggingEnabled(true)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val image = image
            val toDownloadImage = toDownloadImage

            val initialImageToShow = when {
                toDownloadImage?.dateDownload != null -> toDownloadImage
                image?.dateDownload != null -> image
                else -> null
            }
            initialImageToShow?.let {
                log.debug("Initial image shown: ${initialImageToShow.name}")
                showImage(initialImageToShow)
            }

            if (toDownloadImage != null && toDownloadImage != initialImageToShow) {
                downloadAndShowImage(toDownloadImage)
            }
        }
    }

    private fun showImage(image: Image) {
        val basePath = storageService.getDirForLocation(image.storageLocation)
        val imageFilUri = storageService.getFileUri(image)
        val webView = view?.findViewById<WebView>(R.id.image_view)

        if (basePath != null && imageFilUri != null && webView != null) {
            webView.loadDataWithBaseURL(
                basePath.absolutePath,
                HTML_BACKGROUND_CONTAINER.format(imageFilUri),
                "text/html",
                "UTF-8",
                null
            )
            isImageShownInWebView = true
        }
    }

    private suspend fun downloadAndShowImage(image: Image) {
        val issueStub = issueRepository.getIssueStubForImage(image)
        try {
            contentService.downloadSingleFileIfNotDownloaded(
                FileEntry(image), issueStub.baseUrl
            )
            // We have to get the image from the database again to ensure the download state has been stored
            val downloadedImage = imageRepository.get(image.name)
            if (downloadedImage == null) {
                log.warn("Could not get downloaded image $image")
                return
            }

            if (isImageShownInWebView) {
                replaceImage(downloadedImage)
            } else {
                showImage(downloadedImage)
            }

        } catch (e: CacheOperationFailedException) {
            log.warn("Could not download image $image", e)
        }
    }

    private fun replaceImage(image: Image) {
        val imageUri = storageService.getFileUri(image)
        val webView = view?.findViewById<WebView>(R.id.image_view)

        if (imageUri != null && webView != null) {
            // Requires that showImageInWebView has been called before, so that the #image element is present
            webView.evaluateJavascript(
                """
                    document.getElementById("image").src="$imageUri";
                """.trimIndent(),
            ) {
                log.debug("Replaced image with ${image.name}")
            }
        }
    }
}