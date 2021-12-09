package de.taz.app.android.ui.home.page.coverflow

import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.*
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.cover.MOMENT_FADE_DURATION_MS
import de.taz.app.android.util.IssuePublicationMonitor
import de.taz.app.android.util.showIssueDownloadFailedDialog
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadObserver(
    private val fragment: Fragment,
    private val contentService: ContentService,
    private val toastHelper: ToastHelper,
    private val issuePublication: AbstractIssuePublication,
    private val downloadIconView: ImageView,
    private val checkmarkIconView: ImageView,
    private val downloadProgressView: ProgressBar
) {
    constructor(
        fragment: Fragment,
        issuePublication: AbstractIssuePublication,
        downloadIconView: ImageView,
        checkmarkIconView: ImageView,
        downloadProgressView: ProgressBar
    ) : this(
        fragment,
        ContentService.getInstance(fragment.requireContext().applicationContext),
        ToastHelper.getInstance(fragment.requireContext().applicationContext),
        issuePublication, downloadIconView, checkmarkIconView, downloadProgressView
    )

    private val issueCacheLiveData = IssuePublicationMonitor(
        fragment.requireContext().applicationContext,
        issuePublication
    ).issueCacheLiveData

    fun startObserving() {
        hideDownloadIcon()
        issueCacheLiveData.observe(fragment, { update: CacheStateUpdate ->
            var noConnectionShown = false
            fun onConnectionFailure() {
                if (!noConnectionShown) {
                    fragment.lifecycleScope.launch {
                        toastHelper.showNoConnectionToast()
                        noConnectionShown = true
                    }
                }
            }
            setDownloadIconForStatus(update.cacheState)
            when (update.type) {
                CacheStateUpdate.Type.BAD_CONNECTION -> onConnectionFailure()
                else -> Unit
            }
        })
    }

    fun stopObserving() {
        issueCacheLiveData.removeObservers(fragment)
    }


    private fun setDownloadIconForStatus(downloadStatus: CacheState) {
        when (downloadStatus) {
            CacheState.PRESENT -> {
                hideDownloadIcon()
            }
            CacheState.LOADING_CONTENT, CacheState.LOADING_METADATA,
            CacheState.DELETING_METADATA, CacheState.DELETING_CONTENT,
            CacheState.METADATA_PRESENT -> {
                showLoadingIcon()
            }
            CacheState.ABSENT -> {
                showDownloadIcon()
            }
        }
    }

    private fun showDownloadIcon() {
        downloadIconView.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    contentService.downloadToCache(issuePublication)
                } catch (e: CacheOperationFailedException) {
                    withContext(Dispatchers.Main) {
                        fragment.requireActivity().showIssueDownloadFailedDialog(
                            issuePublication
                        )
                    }
                    Sentry.captureException(e, "Download of Issue $issuePublication failed")
                }
            }
        }
        downloadProgressView.visibility = View.GONE
        checkmarkIconView.visibility = View.GONE
        downloadIconView.visibility = View.VISIBLE
    }

    private fun hideDownloadIcon() {
        val wasDownloading = downloadProgressView.visibility == View.VISIBLE
        downloadProgressView.visibility = View.GONE
        checkmarkIconView.visibility = View.GONE
        downloadIconView.visibility = View.GONE

        if (wasDownloading) {
            checkmarkIconView.apply {
                alpha = 1f
                visibility = View.VISIBLE
                animate().alpha(0f).apply {
                    duration = MOMENT_FADE_DURATION_MS
                    startDelay = 2000L
                }
            }
        }
    }

    private fun showLoadingIcon() {
        downloadIconView.visibility = View.GONE
        checkmarkIconView.visibility = View.GONE
        downloadProgressView.visibility = View.VISIBLE
    }
}