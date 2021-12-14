package de.taz.app.android.ui.home.page.coverflow

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.*
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
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

    private val downloadDataStore = DownloadDataStore.getInstance(fragment.requireContext().applicationContext)

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
                    showAutomaticDownloadDialog()
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

    private suspend fun showAutomaticDownloadDialog() {
        val showDialog = !downloadDataStore.pdfDialogDoNotShowAgain.get()
                && issuePublication is IssuePublication

        if (showDialog) {
            withContext(Dispatchers.Main) {
                val dialogView = LayoutInflater.from(fragment.context)
                    .inflate(R.layout.dialog_settings_download_pdf, null)
                val doNotShowAgainCheckboxView =
                    dialogView?.findViewById<MaterialCheckBox>(R.id.dialog_settings_download_pdf_do_not_ask_again)
                val dialog = MaterialAlertDialogBuilder(fragment.requireContext())
                    .setView(dialogView)
                    .setNegativeButton(R.string.settings_dialog_download_too_much_data) { dialog, _ ->
                        setPdfSwitch(
                            doNotShowAgainCheckboxView,
                            issuePublication,
                            enabled = false
                        )
                        dialog.dismiss()
                    }
                    .setPositiveButton(R.string.settings_dialog_download_load_pdf) { dialog, _ ->
                        setPdfSwitch(
                            doNotShowAgainCheckboxView,
                            IssuePublicationWithPages(issuePublication),
                            enabled = true
                        )
                        dialog.dismiss()
                    }
                    .create()
                dialog.show()
            }
        } else {
            contentService.downloadToCache(issuePublication)
        }
    }

    private fun setPdfSwitch(
        doNotShowAgainCheckboxView: MaterialCheckBox?,
        issuePublication: AbstractIssuePublication,
        enabled: Boolean
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            contentService.downloadToCache(issuePublication)
            downloadDataStore.pdfAdditionally.set(enabled)
            downloadDataStore.pdfDialogDoNotShowAgain.set(
                doNotShowAgainCheckboxView?.isChecked == true
            )
        }
    }
}