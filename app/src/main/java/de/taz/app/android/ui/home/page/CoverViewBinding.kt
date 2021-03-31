package de.taz.app.android.ui.home.page

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.RequestManager
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.cover.CoverView
import kotlinx.coroutines.*

interface CoverViewActionListener {
    fun onLongClicked(momentViewData: CoverViewData) = Unit
    fun onImageClicked(momentViewData: CoverViewData) = Unit
    fun onDateClicked(momentViewData: CoverViewData) = Unit
}


abstract class CoverViewBinding<COVER_VIEW : CoverView>(
    private val lifecycleOwner: LifecycleOwner,
    private val issuePublication: IssuePublication,
    private val dateFormat: DateFormat,
    private val glideRequestManager: RequestManager,
    private val onMomentViewActionListener: CoverViewActionListener
) {
    protected var boundView: COVER_VIEW? = null
    protected lateinit var coverViewData: CoverViewData

    private val dataService = DataService.getInstance()
    private var bindJob: Job? = null

    abstract fun onDownloadClicked()
    abstract suspend fun prepareData(): CoverViewData

    fun prepareDataAndBind(view: COVER_VIEW) {
        bindJob = lifecycleOwner.lifecycleScope.launch {
            coverViewData = prepareData()
            bindView(view)
        }
    }

    protected fun dataInitialized(): Boolean {
        return ::coverViewData.isInitialized
    }

    private suspend fun bindView(view: COVER_VIEW) = withContext(Dispatchers.Main) {
        boundView = view
        boundView?.setDate(issuePublication.date, dateFormat)
        boundView?.show(coverViewData, dateFormat, glideRequestManager)

        boundView?.setOnImageClickListener {
            onMomentViewActionListener.onImageClicked(coverViewData)
        }

        boundView?.setOnLongClickListener {
            onMomentViewActionListener.onLongClicked(coverViewData)
            true
        }

        boundView?.setOnDateClickedListener {
            onMomentViewActionListener.onDateClicked(
                coverViewData
            )
        }

        boundView?.setOnDownloadClickedListener { onDownloadClicked() }
        if (boundView?.shouldNotShowDownloadIcon == false) {
            dataService.withDownloadLiveData(coverViewData.issueKey) {
                withContext(Dispatchers.Main) {
                    it.observeDistinct(lifecycleOwner) { downloadStatus ->
                        boundView?.setDownloadIconForStatus(
                            downloadStatus
                        )
                    }
                }
            }
        }
    }

    fun unbind() {
        val exBoundView = boundView
        boundView = null
        bindJob?.cancel()
        exBoundView?.setOnImageClickListener(null)
        exBoundView?.setOnLongClickListener(null)
        exBoundView?.setOnDownloadClickedListener(null)
        exBoundView?.setOnDateClickedListener(null)
        exBoundView?.resetDownloadIcon()
        exBoundView?.clear(glideRequestManager)
    }
}