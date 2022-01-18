package de.taz.app.android.ui.home.page.coverflow

import android.view.LayoutInflater
import android.view.View
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
    private val downloadIconView: View,
    private val checkmarkIconView: View,
    private val downloadProgressView: View
) {
    constructor(
        fragment: Fragment,
        issuePublication: AbstractIssuePublication,
        downloadIconView: View,
        checkmarkIconView: View,
        downloadProgressView:View
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

    private val issueWithPagesCacheLiveData = IssuePublicationMonitor(
        fragment.requireContext().applicationContext,
        IssuePublicationWithPages(issuePublication)
    ).issueCacheLiveData

    private val downloadDataStore =
        DownloadDataStore.getInstance(fragment.requireContext().applicationContext)

    fun startObserving(withPages: Boolean = false) {
        hideDownloadIcon()
        val observingIssueLiveData = if (withPages) issueWithPagesCacheLiveData else issueCacheLiveData
        observingIssueLiveData.observe(fragment, { update: CacheStateUpdate ->
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
        issueWithPagesCacheLiveData.removeObservers(fragment)
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
            stopObserving()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    maybeShowAutomaticDownloadDialog()
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

    private suspend fun maybeShowAutomaticDownloadDialog() {
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
                        setDownloadDataStoreEntriesAndDownloadIssuePublication(
                            doNotShowAgain= doNotShowAgainCheckboxView?.isChecked == true,
                            pdfAdditionally = false,
                            issuePublication = issuePublication
                        )
                        dialog.dismiss()
                    }
                    .setPositiveButton(R.string.settings_dialog_download_load_pdf) { dialog, _ ->
                        setDownloadDataStoreEntriesAndDownloadIssuePublication(
                            doNotShowAgain = doNotShowAgainCheckboxView?.isChecked == true,
                            pdfAdditionally = true,
                            issuePublication = IssuePublicationWithPages(issuePublication)
                        )
                        dialog.dismiss()
                    }
                    .create()
                dialog.show()
            }
        } else {
            withContext(Dispatchers.Main) {
                startObserving(
                    withPages = downloadDataStore.pdfAdditionally.get()
                )
            }
            contentService.downloadIssuePublicationToCache(issuePublication)
        }
    }

    private fun setDownloadDataStoreEntriesAndDownloadIssuePublication(
        doNotShowAgain: Boolean,
        pdfAdditionally: Boolean,
        issuePublication: AbstractIssuePublication
    ) {
        startObserving(withPages = pdfAdditionally)
        CoroutineScope(Dispatchers.IO).launch {
            downloadDataStore.pdfAdditionally.set(pdfAdditionally)
            downloadDataStore.pdfDialogDoNotShowAgain.set(doNotShowAgain)
            contentService.downloadIssuePublicationToCache(issuePublication)
        }
    }
}