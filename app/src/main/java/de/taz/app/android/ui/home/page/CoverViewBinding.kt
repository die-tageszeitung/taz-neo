package de.taz.app.android.ui.home.page

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.RequestManager
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.cover.CoverView
import kotlinx.coroutines.*

interface CoverViewActionListener {
    fun onLongClicked(momentViewData: CoverViewData) = Unit
    fun onImageClicked(momentViewData: CoverViewData) = Unit
    fun onDateClicked(momentViewData: CoverViewData) = Unit
}


abstract class CoverViewBinding(
    applicationContext: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val dateFormat: DateFormat,
    private val glideRequestManager: RequestManager,
    private val onMomentViewActionListener: CoverViewActionListener
) {
    protected var boundView: CoverView? = null
    private lateinit var coverViewData: CoverViewData

    private val dataService = DataService.getInstance(applicationContext)
    private var bindJob: Job? = null

    abstract fun onDownloadClicked()
    abstract suspend fun prepareData(): CoverViewData

    fun prepareDataAndBind(view: CoverView) {
        bindJob = lifecycleOwner.lifecycleScope.launch {
            coverViewData = prepareData()
            bindView(view)
        }
    }

    protected fun dataInitialized(): Boolean {
        return ::coverViewData.isInitialized
    }

    private suspend fun bindView(view: CoverView) = withContext(Dispatchers.Main) {
        boundView = view.apply {
            show(coverViewData, dateFormat, glideRequestManager)

            setOnImageClickListener {
                onMomentViewActionListener.onImageClicked(coverViewData)
            }
            setOnLongClickListener {
                onMomentViewActionListener.onLongClicked(coverViewData)
                true
            }
            setOnDateClickedListener {
                onMomentViewActionListener.onDateClicked(coverViewData)
            }
            setOnDownloadClickedListener { onDownloadClicked() }
            if (!shouldNotShowDownloadIcon) {
                dataService.withDownloadLiveData(coverViewData.issueKey) {
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
    }

    fun unbind() {
        val exBoundView = boundView
        boundView = null
        bindJob?.cancel()
        exBoundView?.apply {
            setOnImageClickListener(null)
            setOnLongClickListener(null)
            setOnDownloadClickedListener(null)
            setOnDateClickedListener(null)
            clear()
        }
    }
}