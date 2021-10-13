package de.taz.app.android.ui.home.page.coverflow

import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import de.taz.app.android.R
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.api.models.IssueWithPages
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.persistence.repository.IssueKeyWithPages
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.cover.MOMENT_FADE_DURATION_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DownloadObserver(
    private val lifecycleOwner: LifecycleOwner,
    private val dataService: DataService,
    private val toastHelper: ToastHelper,
    private val issueKey: AbstractIssueKey
) {
    private var boundView: View? = null
    private var observer: Observer<DownloadStatus>? = null
    private var liveData: LiveData<DownloadStatus>? = null


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
        lifecycleOwner.lifecycleScope.launch (Dispatchers.IO) {
            dataService.withDownloadLiveData(
                issueKey
            ) {
                withContext(Dispatchers.Main) {
                    it.observeDistinct(lifecycleOwner) { downloadStatus ->
                        setDownloadIconForStatus(
                            downloadStatus
                        )
                    }
                }
            }
        }
    }

    fun unbindView() {
        boundView = null
        observer?.let {
            liveData?.removeObserver(it)
        }
    }

    private fun setDownloadIconForStatus(downloadStatus: DownloadStatus) {
        when (downloadStatus) {
            DownloadStatus.done -> hideDownloadIcon()
            DownloadStatus.started -> showLoadingIcon()
            else -> showDownloadIcon()
        }
    }

    private fun showDownloadIcon() {
        var noConnectionShown = false
        fun onConnectionFailure() {
            if (!noConnectionShown) {
                lifecycleOwner.lifecycleScope.launch {
                    toastHelper.showNoConnectionToast()
                    noConnectionShown = true
                }
            }
        }
        boundView?.setOnClickListener {
                CoroutineScope(Dispatchers.IO).launch {
                    // we refresh the issue from network, as the cache might be pretty stale at this point (issues might be edited after release)
                    val observedIssue = run {
                        val issue = dataService.getIssue(
                            IssuePublication(issueKey),
                            retryOnFailure = true,
                            allowCache = false,
                            forceUpdate = true,
                            onConnectionFailure = { onConnectionFailure() },
                            cacheWithPages = issueKey is IssueKeyWithPages
                        )
                        if (issueKey is IssueKeyWithPages) {
                            IssueWithPages(issue)
                        } else {
                            issue
                        }
                    }
                    dataService.ensureDownloaded(
                        collection = observedIssue,
                        onConnectionFailure = { onConnectionFailure() }
                    )
                }
                showLoadingIcon()
        }
        downloadProgressView?.visibility = View.GONE
        checkmarkIconView?.visibility = View.GONE
        downloadIconView?.visibility = View.VISIBLE
    }

    private fun hideDownloadIcon(reset: Boolean = false) {
        boundView?.setOnClickListener(null)
        val wasDownloading = downloadProgressView?.visibility == View.VISIBLE
        downloadProgressView?.visibility = View.GONE
        checkmarkIconView?.visibility = View.GONE
        downloadIconView?.visibility = View.GONE

        if (wasDownloading && !reset) {
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