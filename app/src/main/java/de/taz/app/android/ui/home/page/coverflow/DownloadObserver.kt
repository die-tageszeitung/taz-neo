package de.taz.app.android.ui.home.page.coverflow

import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.*
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.cover.MOMENT_FADE_DURATION_MS
import de.taz.app.android.util.Log
import de.taz.app.android.util.showIssueDownloadFailedDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadObserver(
    private val fragment: Fragment,
    private val contentService: ContentService,
    private val toastHelper: ToastHelper,
    private val issueKey: AbstractIssueKey
) {
    private var boundView: View? = null
    private val log by Log

    private val downloadIconView: ImageView?
        get() {
            return boundView?.findViewById(R.id.fragment_coverflow_moment_download)
        }
    private val checkmarkIconView: ImageView?
        get() {
            return boundView?.findViewById(R.id.fragment_coverflow_moment_download_finished)
        }
    private val downloadProgressView: ProgressBar?
        get() {
            return boundView?.findViewById(R.id.fragment_coverflow_moment_downloading)
        }

    fun bindView(view: View) {
        boundView = view
        hideDownloadIcon()
        var noConnectionShown = false
        fun onConnectionFailure() {
            if (!noConnectionShown) {
                fragment.lifecycleScope.launch {
                    toastHelper.showNoConnectionToast()
                    noConnectionShown = true
                }
            }
        }
        contentService.getCacheStatusFlow(issueKey)
            .asLiveData(Dispatchers.Main)
            .observe(fragment) {
                setDownloadIconForStatus(it.cacheState)
                when (it.type) {
                    CacheStateUpdate.Type.BAD_CONNECTION -> onConnectionFailure()
                    else -> Unit
                }
            }
    }


    fun unbindView() {
        boundView = null
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
        boundView?.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    contentService.downloadToCacheIfNotPresent(issueKey)
                } catch (e: CacheOperationFailedException) {
                    withContext(Dispatchers.Main) {
                        fragment.requireActivity().showIssueDownloadFailedDialog(issueKey)
                    }
                }
            }
        }
        downloadProgressView?.visibility = View.GONE
        checkmarkIconView?.visibility = View.GONE
        downloadIconView?.visibility = View.VISIBLE
    }

    private fun hideDownloadIcon() {
        boundView?.setOnClickListener(null)
        val wasDownloading = downloadProgressView?.visibility == View.VISIBLE
        downloadProgressView?.visibility = View.GONE
        checkmarkIconView?.visibility = View.GONE
        downloadIconView?.visibility = View.GONE

        if (wasDownloading) {
            checkmarkIconView?.apply {
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
        boundView?.setOnClickListener(null)
        downloadIconView?.visibility = View.GONE
        checkmarkIconView?.visibility = View.GONE
        downloadProgressView?.visibility = View.VISIBLE
    }
}