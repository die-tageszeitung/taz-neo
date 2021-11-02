package de.taz.app.android.ui.home.page

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.RequestManager
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheStateUpdate
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.ToastHelper
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
    private var boundView: CoverView? = null
    protected lateinit var coverViewData: CoverViewData

    private val contentService = ContentService.getInstance(applicationContext)
    private val toastHelper = ToastHelper.getInstance(applicationContext)
    private var bindJob: Job? = null
    private var noConnectionShown = false

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

    fun onConnectionFailure() {
        if (!noConnectionShown) {
            lifecycleOwner.lifecycleScope.launch {
                toastHelper.showNoConnectionToast()
                noConnectionShown = true
            }
        }
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
                contentService
                    .getCacheStatusFlow(coverViewData.issueKey)
                    .asLiveData()
                    .observe(lifecycleOwner) {
                        setDownloadIconForStatus(
                            it.cacheState
                        )
                        if (it.type == CacheStateUpdate.Type.BAD_CONNECTION) {
                            onConnectionFailure()
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