package de.taz.app.android.ui.home.page

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.RequestManager
import de.taz.app.android.DEFAULT_MOMENT_FILE
import de.taz.app.android.METADATA_DOWNLOAD_DEFAULT_RETRIES
import de.taz.app.android.R
import de.taz.app.android.api.models.Moment
import de.taz.app.android.api.models.Page
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.download.DownloadPriority
import de.taz.app.android.persistence.repository.AbstractCoverPublication
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.FrontpagePublication
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.persistence.repository.MomentPublication
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.cover.CoverView
import de.taz.app.android.ui.home.page.coverflow.DownloadObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface CoverViewActionListener {
    fun onLongClicked(coverPublication: AbstractCoverPublication) = Unit
    fun onImageClicked(coverPublication: AbstractCoverPublication) = Unit
    fun onDateClicked(coverPublication: AbstractCoverPublication) = Unit
    fun onContinueReadClicked(coverPublication: AbstractCoverPublication) = Unit
}


class CoverViewBinding(
    private val fragment: Fragment,
    private val coverPublication: AbstractCoverPublication,
    private val coverViewDate: CoverViewDate,
    private val glideRequestManager: RequestManager,
    private val onMomentViewActionListener: CoverViewActionListener,
    private val observeDownloads: Boolean = true,
) {
    private var boundView: CoverView? = null
    private lateinit var coverViewData: CoverViewData

    private val applicationContext: Context = fragment.requireContext().applicationContext

    private val fileEntryRepository = FileEntryRepository.getInstance(applicationContext)
    private val storageService = StorageService.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)
    private var bindJob: Job? = null

    private var downloadObserver: DownloadObserver? = null

    private suspend fun prepareData(): CoverViewData {
        // ensure metadata downloaded
        val download = contentService.downloadMetadata(
            coverPublication,
            maxRetries = METADATA_DOWNLOAD_DEFAULT_RETRIES
        )
        contentService.downloadToCache(download, DownloadPriority.High)

        // Refresh from db
        val observableDownload =
            contentService.downloadMetadata(coverPublication)

        return when (observableDownload) {
            is Page -> {
                val fileEntry = fileEntryRepository.get(observableDownload.pagePdf.name)
                val pdfMomentFilePath = fileEntry?.let { storageService.getAbsolutePath(it) }

                val momentType = CoverType.FRONT_PAGE
                CoverViewData(
                    momentType,
                    pdfMomentFilePath,
                    observableDownload.dateDownload
                )
            }
            is Moment -> {
                val momentImagePath = observableDownload.getMomentImage()?.let {
                    storageService.getAbsolutePath(it)
                }
                val animatedMomentPath = observableDownload.getIndexHtmlForAnimated()?.let {
                    storageService.getAbsolutePath(it)
                }
                // Get the setting if user wants to show animated moments (default is true)
                val settingsShowAnimatedMoments =
                    GeneralDataStore.getInstance(applicationContext).showAnimatedMoments.get()

                val momentType = if (animatedMomentPath != null && settingsShowAnimatedMoments) {
                    CoverType.ANIMATED
                } else {
                    CoverType.STATIC
                }

                val momentPath = when (momentType) {
                    CoverType.ANIMATED -> animatedMomentPath
                    CoverType.STATIC -> momentImagePath
                    else -> throw IllegalStateException("MomentViewDataBinding only supports ANIMATED and STATIC")
                }

                CoverViewData(
                    momentType,
                    momentPath,
                    observableDownload.dateDownload
                )
            }
            else ->
                throw IllegalStateException("CoverViewBinding trying to show neither Moment nor a Page")
        }
    }

    fun prepareDataAndBind(view: CoverView) {
        bindJob = fragment.lifecycleScope.launch {
            coverViewData = try {
                prepareData()
            } catch (e: CacheOperationFailedException) {
                // maxRetries reached - so show the fallback cover view:
                val moment = fileEntryRepository.get(DEFAULT_MOMENT_FILE)
                val momentUri = moment?.let { storageService.getFileUri(it) }
                CoverViewData(
                    CoverType.STATIC,
                    momentUri,
                    moment?.dateDownload
                )
            }
            bindView(view)
        }
    }

    private suspend fun bindView(view: CoverView) = withContext(Dispatchers.Main) {
        boundView = view.apply {
            show(coverViewData, coverViewDate, glideRequestManager)

            setOnImageClickListener {
                onMomentViewActionListener.onImageClicked(coverPublication)
            }
            setOnLongClickListener {
                onMomentViewActionListener.onLongClicked(coverPublication)
                true
            }
            setOnDateClickedListener {
                onMomentViewActionListener.onDateClicked(coverPublication)
            }

            val issuePublication = when (coverPublication) {
                is FrontpagePublication -> IssuePublicationWithPages(
                    coverPublication.feedName,
                    coverPublication.date
                )

                is MomentPublication -> IssuePublication(
                    coverPublication.feedName,
                    coverPublication.date
                )

                else -> throw IllegalStateException("Unknown publication type ${coverPublication::class.simpleName}")
            }

            if (observeDownloads) {
                downloadObserver = DownloadObserver(
                    fragment,
                    issuePublication,
                    view.findViewById(R.id.view_moment_download),
                    view.findViewById(R.id.view_moment_download_finished),
                    view.findViewById(R.id.view_moment_downloading),
                    view.findViewById(R.id.view_moment_continue_read)
                ).apply {
                    startObserving()
                }
            }
        }
    }

    fun unbind() {
        val exBoundView = boundView
        downloadObserver?.stopObserving()
        downloadObserver = null
        bindJob?.cancel()
        boundView = null
        exBoundView?.apply {
            setOnImageClickListener(null)
            setOnLongClickListener(null)
            setOnDateClickedListener(null)
            clear()
        }
    }
}