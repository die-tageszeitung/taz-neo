package de.taz.app.android.ui.share

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.base.ViewBindingBottomSheetFragment
import de.taz.app.android.databinding.FragmentBottomSheetShareOptionsBinding
import de.taz.app.android.monkey.setBehaviorStateOnLandscape
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.Log
import de.taz.app.android.util.showConnectionErrorDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class ShareArticleBottomSheet :
    ViewBindingBottomSheetFragment<FragmentBottomSheetShareOptionsBinding>() {

    companion object {
        // Shared arguments
        private const val ARGUMENT_ARTICLE_KEY = "articleKey"
        private const val ARGUMENT_ARTICLE_MEDIA_SYNC_ID = "articleMediaSyncId"
        private const val ARGUMENT_ARTICLE_TITLE = "articleTitle"
        private const val ARGUMENT_ARTICLE_ONLINE_URL = "articleOnlineUrl"

        // SearchHit arguments
        private const val ARGUMENT_ARTICLE_PDF_NAME = "articlePdfName"
        private const val ARGUMENT_ARTICLE_PDF_BASE_URL = "articlePdfBaseUrl"

        // The [ShareArticleBottomSheet] will be dismissed with this delay after the intent to show
        // the Android Sharesheet is started.
        private const val DISMISS_AFTER_SHARESHEET_MS = 250L

        const val TAG = "ShareArticleBottomSheet"

        fun newInstance(articleStub: ArticleStub): DialogFragment =
            if (isShareable(articleStub)) {
                ShareArticleBottomSheet().apply {
                    arguments = bundleOf(
                        ARGUMENT_ARTICLE_KEY to articleStub.key,
                        ARGUMENT_ARTICLE_TITLE to articleStub.title,
                        ARGUMENT_ARTICLE_ONLINE_URL to articleStub.onlineLink,
                        ARGUMENT_ARTICLE_MEDIA_SYNC_ID to articleStub.mediaSyncId,
                    )
                }
            } else {
                SharingNotPossibleDialogFragment()
            }

        fun newInstance(searchHit: SearchHit): DialogFragment =
            if (isShareable(searchHit)) {
                ShareArticleBottomSheet().apply {
                    arguments = bundleOf(
                        ARGUMENT_ARTICLE_KEY to searchHit.articleFileName,
                        ARGUMENT_ARTICLE_TITLE to searchHit.title,
                        ARGUMENT_ARTICLE_ONLINE_URL to searchHit.onlineLink,
                        ARGUMENT_ARTICLE_MEDIA_SYNC_ID to searchHit.mediaSyncId,
                        ARGUMENT_ARTICLE_PDF_BASE_URL to searchHit.baseUrl,
                        ARGUMENT_ARTICLE_PDF_NAME to searchHit.articlePdfFileName,
                    )
                }
            } else {
                SharingNotPossibleDialogFragment()
            }

        fun isShareable(articleStub: ArticleStub): Boolean =
            articleStub.onlineLink != null || articleStub.pdfFileName != null

        fun isShareable(searchHit: SearchHit): Boolean =
            searchHit.onlineLink != null || searchHit.articlePdfFileName != null

        fun isShareable(article: Article): Boolean =
            article.onlineLink != null || article.pdf != null
    }

    private val log by Log

    private lateinit var articleRepository: ArticleRepository
    private lateinit var shareArticleDownloadHelper: ShareArticleDownloadHelper
    private lateinit var tracker: Tracker

    override fun onStart() {
        super.onStart()
        setBehaviorStateOnLandscape(BottomSheetBehavior.STATE_EXPANDED)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        articleRepository = ArticleRepository.getInstance(context.applicationContext)
        shareArticleDownloadHelper = ShareArticleDownloadHelper(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val articleKey = arguments?.getString(ARGUMENT_ARTICLE_KEY)
        val articleOnlineUrl = arguments?.getString(ARGUMENT_ARTICLE_ONLINE_URL)
        val articlePdfName = arguments?.getString(ARGUMENT_ARTICLE_PDF_NAME)
        val articlePdfBaseUrl = arguments?.getString(ARGUMENT_ARTICLE_PDF_BASE_URL)

        var sharingAvailable = false

        if (articleOnlineUrl != null) {
            viewBinding.apply {
                shareUrlGroup.isVisible = true
                shareUrl.setOnClickListener {
                    shareUrl(articleOnlineUrl)
                }
            }
            sharingAvailable = true
        }

        if (articlePdfName != null && articlePdfBaseUrl != null) {
            viewBinding.apply {
                sharePdfGroup.isVisible = true
                sharePdf.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        shareSearchHitPdf(articlePdfName, articlePdfBaseUrl)
                    }
                }
            }
            sharingAvailable = true

        } else if (articleKey != null) {
            viewBinding.apply {
                sharePdfGroup.isVisible = true
                sharePdf.setOnClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        shareArticlePdf(articleKey)
                    }
                }
            }
            sharingAvailable = true
        }

        if (!sharingAvailable) {
            // Show the other dialog and dismiss this BottomSheet
            SharingNotPossibleDialogFragment().show(parentFragmentManager, null)
            dismiss()
        }
    }

    private fun shareUrl(url: String) {
        val context = requireContext()

        val articleFileName = arguments?.getString(ARGUMENT_ARTICLE_KEY)
        val articleMediaSyncId =
            arguments?.getInt(ARGUMENT_ARTICLE_MEDIA_SYNC_ID)?.takeIf { it > 0 }
        val articleTitle = arguments?.getString(ARGUMENT_ARTICLE_TITLE)

        if (articleFileName != null) {
            tracker.trackShareArticleLinkEvent(articleFileName, articleMediaSyncId)
        }

        ShareCompat.IntentBuilder(context)
            .setType("text/plain")
            .setText(url)
            .setChooserTitle(articleTitle)
            .setSubject(articleTitle)
            .startChooser()

        viewLifecycleOwner.lifecycleScope.launch {
            dismissAfterDelay()
        }
    }

    private suspend fun shareArticlePdf(articleKey: String) {
        showLoading()

        try {
            val articleStub = articleRepository.getStub(articleKey)
                ?: throw Exception("No ArticleStub for $articleKey")

            tracker.trackShareArticlePdfEvent(articleStub.articleFileName, articleStub.mediaSyncId)

            val cachedArticlePdfFile = shareArticleDownloadHelper.downloadArticlePdf(articleStub)
            shareCachedPdfFile(cachedArticlePdfFile, articleStub.title)

        } catch (e: Exception) {
            log.error("Article PDF download failed", e)
            showPdfDownloadError()
        }
    }


    private suspend fun shareSearchHitPdf(articlePdfName: String, articlePdfBaseUrl: String) {
        showLoading()

        val articleKey = arguments?.getString(ARGUMENT_ARTICLE_KEY)
        val articleMediaSyncId =
            arguments?.getInt(ARGUMENT_ARTICLE_MEDIA_SYNC_ID)?.takeIf { it > 0 }
        val articleTitle = arguments?.getString(ARGUMENT_ARTICLE_TITLE)

        if (articleKey != null) {
            tracker.trackShareArticlePdfEvent(articleKey, articleMediaSyncId)
        }

        try {
            val cachedArticlePdfFile =
                shareArticleDownloadHelper.downloadArticlePdf(articlePdfName, articlePdfBaseUrl)
            shareCachedPdfFile(cachedArticlePdfFile, articleTitle)

        } catch (e: Exception) {
            log.error("Article PDF download failed", e)
            showPdfDownloadError()
        }
    }

    private suspend fun shareCachedPdfFile(cachedFile: File, title: String?) {
        val context = requireContext()

        val applicationId = context.packageName
        val shareUri = FileProvider.getUriForFile(
            context,
            "${applicationId}.contentProvider",
            cachedFile
        )

        ShareCompat.IntentBuilder(context)
            .setType("application/pdf")
            .setStream(shareUri)
            .setChooserTitle(title)
            .setSubject(title)
            .startChooser()

        dismissAfterDelay()
    }

    private fun showLoading() {
        viewBinding.loadingScreen.root.isVisible = true
    }

    private fun hideLoading() {
        viewBinding.loadingScreen.root.isVisible = false
    }

    private fun showPdfDownloadError() {
        activity?.showConnectionErrorDialog {
            dismiss()
        }
    }

    private fun showError(@StringRes stringId: Int) {
        hideShareOptions()
        hideLoading()
        viewBinding.shareError.apply {
            isVisible = true
            setText(stringId)
            setOnClickListener {
                dismiss()
            }
        }
    }

    private fun hideShareOptions() {
        viewBinding.apply {
            shareUrlGroup.isVisible = false
            sharePdfGroup.isVisible = false
            shareTextGroup.isVisible = false
        }
    }

    private suspend fun dismissAfterDelay() {
        delay(DISMISS_AFTER_SHARESHEET_MS)
        dismiss()
    }
}