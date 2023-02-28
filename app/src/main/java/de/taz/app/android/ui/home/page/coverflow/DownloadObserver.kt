package de.taz.app.android.ui.home.page.coverflow

import android.view.LayoutInflater
import android.view.View
import androidx.annotation.MainThread
import androidx.fragment.app.Fragment
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperation
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.content.cache.CacheState
import de.taz.app.android.content.cache.CacheStateUpdate
import de.taz.app.android.dataStore.DownloadDataStore
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.ui.cover.MOMENT_FADE_DURATION_MS
import de.taz.app.android.util.Log
import de.taz.app.android.util.showIssueDownloadFailedDialog
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private enum class IssueDownloadStatus {
    PRESENT, ABSENT, LOADING
}

class DownloadObserver constructor(
    private val fragment: Fragment,
    private val issuePublication: AbstractIssuePublication,
    private val downloadIconView: View,
    private val checkmarkIconView: View,
    private val downloadProgressView: View
) {
    private val log by Log

    private val contentService =
        ContentService.getInstance(fragment.requireContext().applicationContext)
    private val downloadDataStore =
        DownloadDataStore.getInstance(fragment.requireContext().applicationContext)
    private val issueRepository =
        IssueRepository.getInstance(fragment.requireContext().applicationContext)
    private val authHelper =
        AuthHelper.getInstance(fragment.requireContext().applicationContext)

    private val issueDownloadStatusFlow = MutableStateFlow(IssueDownloadStatus.PRESENT)
    private var issueDownloadStatusObserverJob: Job? = null

    private var currentMinIssueStatus = IssueStatus.public
    private var minIssueStatusObserverJob: Job? = null

    private var issueChangesObserverJob: Job? = null

    private var isObserving = false

    fun startObserving() {
        if (!isObserving) {
            isObserving = true
            startIssueDownloadStatusObserver()
            startMinIssueStatusObserver()
        }
    }

    fun stopObserving() {
        stopIssueDownloadStatusObserver()
        stopMinIssueStatusObserver()
        stopIssueChangesObserverJob()
        isObserving = false
    }

    // Determines the tags to observe for this [issuePublication]
    // Has to be in sync with the [getDownloadTag] methods defined in [IssueRepository]
    // WARNING: we are only listening to tags without status information, as we know that we
    // are only downloading issues based on [AbstractIssuePublication]s as is enforced on
    // [WrappedDownload.prepare]
    private val tags: List<String> = run {
        val baseTag = "${issuePublication.feedName}/${issuePublication.date}"
        val parentTag = "parent/$baseTag"
        val pdfTag = "$baseTag/pdf"
        val parentPdfTag = "parent/$pdfTag"
        when (issuePublication) {
            is IssuePublication -> listOf(parentTag, parentPdfTag)
            is IssuePublicationWithPages -> listOf(parentPdfTag)
            else -> error("Unknown issuePublication: $issuePublication")
        }
    }

    /**
     * Test if there exists a downloaded [IssueStub] for this [issuePublication] and the [currentMinIssueStatus].
     *
     * @return IssueStub if it is successfully downloaded,
     *         null otherwise
     */
    private suspend fun getIssueStubIfDownloaded(): IssueStub? {
        val issueStub = issueRepository.getMostValuableIssueStubForPublication(issuePublication)
        return if (isIssueStubDownloaded(issueStub)) {
            issueStub
        } else {
            null
        }
    }

    /**
     * Test if the [issueStub] is marked as downloaded and matches the [currentMinIssueStatus].
     */
    private fun isIssueStubDownloaded(issueStub: IssueStub?): Boolean {
        return issueStub != null
                && issueStub.status >= currentMinIssueStatus
                && ((issuePublication is IssuePublication && issueStub.dateDownload != null)
                || (issuePublication is IssuePublicationWithPages && issueStub.dateDownloadWithPages != null))
    }

    /**
     * Test if any of the [tags] related with [issuePublication] is currently being processed by
     * the cache system (aka being downloaded or being deleted).
     */
    private fun isActiveCacheOperation(): Boolean {
        return tags.any { CacheOperation.activeCacheOperations.containsKey(it) }
    }

    /**
     * Listen to changes on the [issueDownloadStatusFlow] and update the UI
     */
    private fun startIssueDownloadStatusObserver() {
        stopIssueDownloadStatusObserver()
        issueDownloadStatusObserverJob = fragment.lifecycleScope.launch(Dispatchers.Main) {
            issueDownloadStatusFlow.collect {
                when (it) {
                    IssueDownloadStatus.PRESENT -> hideDownloadIcon()
                    IssueDownloadStatus.ABSENT -> showDownloadIcon()
                    IssueDownloadStatus.LOADING -> showLoadingIcon()
                }
            }
        }
    }

    private fun stopIssueDownloadStatusObserver() {
        issueDownloadStatusObserverJob?.cancel()
        issueDownloadStatusObserverJob = null
    }

    /**
     * Listen to changes to to global minimum issue status and updates the [currentMinIssueStatus]
     * and re-initializes the observers and state by calling [onMinStateChange].
     *
     * The issue status changes when users login or out.
     * For anonymous users every Issue with at least a public state is "PRESENT"
     * For logged in users only Issues with at least a demo or regular state is "PRESENT"
     *
     */
    private fun startMinIssueStatusObserver() {
        stopMinIssueStatusObserver()
        minIssueStatusObserverJob = fragment.lifecycleScope.launch(Dispatchers.Default) {
            authHelper.minStatusLiveData.asFlow().distinctUntilChanged().collect {
                currentMinIssueStatus = it
                onMinStateChange()
            }
        }
    }

    private fun stopMinIssueStatusObserver() {
        minIssueStatusObserverJob?.cancel()
        minIssueStatusObserverJob = null
    }

    private fun stopIssueChangesObserverJob() {
        issueChangesObserverJob?.cancel()
        issueChangesObserverJob = null
    }

    /**
     * Check the current issue download state and re-start the observers
     */
    private suspend fun onMinStateChange() {
        stopIssueChangesObserverJob()

        val downloadedIssueStub = getIssueStubIfDownloaded()
        if (downloadedIssueStub != null) {
            issueDownloadStatusFlow.value = IssueDownloadStatus.PRESENT
            startObserverForDatabaseIssueChanges(downloadedIssueStub.issueKey)
        } else {
            if (isActiveCacheOperation()) {
                issueDownloadStatusFlow.value = IssueDownloadStatus.LOADING
            } else {
                issueDownloadStatusFlow.value = IssueDownloadStatus.ABSENT
            }
            startObserverForRelatedIssueCacheStatusChanges()
        }
    }

    /**
     * Start listening for changes to the issue state for the issue defined by [issueKey].
     * This function should be called when a downloaded issue was already found with [getIssueStubIfDownloaded].
     * If the database entry for the issue is changing and the issue is no longer downloaded,
     * control will be passed on the [startObserverForRelatedIssueCacheStatusChanges].
     */
    private fun startObserverForDatabaseIssueChanges(issueKey: IssueKey) {
        stopIssueChangesObserverJob()
        issueChangesObserverJob = fragment.lifecycleScope.launch(Dispatchers.Default) {
            issueRepository.getStubFlow(issueKey.feedName, issueKey.date, issueKey.status)
                .collect {
                    if (!isIssueStubDownloaded(it)) {
                        // Eagerly set the issue download status to LOADING if the issue is no longer
                        // downloaded (for the [currentMinIssueStatus]) but a related tag is already
                        // being processed by the cache system.
                        if (isActiveCacheOperation()) {
                            issueDownloadStatusFlow.value = IssueDownloadStatus.LOADING
                        } else {
                            issueDownloadStatusFlow.value = IssueDownloadStatus.ABSENT
                        }
                        startObserverForRelatedIssueCacheStatusChanges()
                    } else {
                        issueDownloadStatusFlow.value = IssueDownloadStatus.PRESENT
                    }
                }
        }
    }

    /**
     * Start listening for [CacheStateUpdate] updates on [tags] related to this [issuePublication].
     * If a state that might result in the issue being downloaded is encountered, the database is
     * will be checked to determine if the the issue is downloaded. If yes control will be passed on
     * to [startObserverForDatabaseIssueChanges].
     *
     * This observer has to be running while the issue is not downloaded, so that is can immediately
     * show the correct loading state once the download is triggered from anywhere in the app.
     */
    private fun startObserverForRelatedIssueCacheStatusChanges() {
        stopIssueChangesObserverJob()
        issueChangesObserverJob = fragment.lifecycleScope.launch(Dispatchers.Default) {
            CacheOperation.cacheStatusFlow.filter { (tag, _) ->
                tags.contains(tag)
            }.collect { (_, status) ->
                when (status.cacheState) {
                    CacheState.ABSENT ->
                        issueDownloadStatusFlow.value = IssueDownloadStatus.ABSENT
                    CacheState.LOADING_CONTENT -> issueDownloadStatusFlow.value =
                        IssueDownloadStatus.LOADING
                    CacheState.PRESENT -> {
                        val downloadedStub = getIssueStubIfDownloaded()
                        if (downloadedStub != null) {
                            issueDownloadStatusFlow.value = IssueDownloadStatus.PRESENT
                            startObserverForDatabaseIssueChanges(downloadedStub.issueKey)
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    @MainThread
    private fun showDownloadIcon() {
        downloadIconView.setOnClickListener {
            stopObserving()
            fragment.lifecycleScope.launch(Dispatchers.Main) {
                try {
                    maybeShowAutomaticDownloadDialog()
                } catch (e: CacheOperationFailedException) {
                    fragment.requireActivity().showIssueDownloadFailedDialog(issuePublication)
                    Sentry.captureException(e, "Download of Issue $issuePublication failed")
                }
            }
        }
        downloadProgressView.visibility = View.GONE
        checkmarkIconView.visibility = View.GONE
        downloadIconView.visibility = View.VISIBLE
    }

    @MainThread
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

    @MainThread
    private fun showLoadingIcon() {
        if (downloadProgressView.visibility != View.VISIBLE) {
            downloadIconView.visibility = View.GONE
            checkmarkIconView.visibility = View.GONE
            downloadProgressView.visibility = View.VISIBLE
        }
    }

    private suspend fun maybeShowAutomaticDownloadDialog() {
        val showDialog =
            !downloadDataStore.pdfDialogDoNotShowAgain.get() && issuePublication is IssuePublication

        if (showDialog) {
            val dialogView = LayoutInflater.from(fragment.context)
                .inflate(R.layout.dialog_settings_download_pdf, null)
            val doNotShowAgainCheckboxView =
                dialogView?.findViewById<MaterialCheckBox>(R.id.dialog_settings_download_pdf_do_not_ask_again)
            val dialog =
                MaterialAlertDialogBuilder(fragment.requireContext()).setView(dialogView)
                    .setNegativeButton(R.string.settings_dialog_download_too_much_data) { dialog, _ ->
                        setDownloadDataStoreEntriesAndDownloadIssuePublication(
                            doNotShowAgain = doNotShowAgainCheckboxView?.isChecked == true,
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
                    }.create()
            dialog.show()
        } else {
            startObserving()
            downloadIssuePublication(issuePublication)
        }
    }

    private fun setDownloadDataStoreEntriesAndDownloadIssuePublication(
        doNotShowAgain: Boolean,
        pdfAdditionally: Boolean,
        issuePublication: AbstractIssuePublication
    ) {
        startObserving()
        fragment.lifecycleScope.launch {
            downloadDataStore.pdfAdditionally.set(pdfAdditionally)
            downloadDataStore.pdfDialogDoNotShowAgain.set(doNotShowAgain)
            downloadIssuePublication(issuePublication)
        }
    }

    // Try to download the issue publication and catch and log _any exception_
    // The exception is not re-thrown!
    private suspend fun downloadIssuePublication(issuePublication: AbstractIssuePublication) {
        try {
            // FIXME: maybe show the loading state immediately
            contentService.downloadIssuePublicationToCache(issuePublication)
        } catch (e: Exception) {
            val hint = "Exception while downloading an issue publication from the download observer"
            log.error(hint, e)
            Sentry.captureException(e, hint)
        }
    }
}