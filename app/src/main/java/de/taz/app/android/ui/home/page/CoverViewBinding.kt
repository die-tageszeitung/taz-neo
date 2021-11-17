package de.taz.app.android.ui.home.page

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.RequestManager
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheStateUpdate
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.cover.CoverView
import io.sentry.Sentry
import kotlinx.coroutines.*

interface CoverViewActionListener {
    fun onLongClicked(momentViewData: CoverViewData) = Unit
    fun onImageClicked(momentViewData: CoverViewData) = Unit
    fun onDateClicked(momentViewData: CoverViewData) = Unit
}

class CoverBindingException(
    message: String = "Binding cover data failed",
    cause: Exception?
) : Exception(message, cause)


abstract class CoverViewBinding(
    private val fragment: Fragment,
    protected val coverPublication: AbstractIssuePublication,
    private val dateFormat: DateFormat,
    private val glideRequestManager: RequestManager,
    private val onMomentViewActionListener: CoverViewActionListener
) {
    private var boundView: CoverView? = null
    protected lateinit var coverViewData: CoverViewData

    protected val applicationContext: Context = fragment.requireContext().applicationContext

    private val contentService = ContentService.getInstance(applicationContext)
    private val toastHelper = ToastHelper.getInstance(applicationContext)
    private var bindJob: Job? = null
    private var noConnectionShown = false
    private var issueLiveData: LiveData<CacheStateUpdate>? = null

    abstract fun onDownloadClicked()
    abstract suspend fun prepareData(): CoverViewData

    fun prepareDataAndBind(view: CoverView) {
        bindJob = fragment.lifecycleScope.launch {
            try {
                coverViewData = prepareData()
                bindView(view)
            } catch (e: CoverBindingException) {
                val hint = "Binding cover failed on $coverPublication"
                e.printStackTrace()
                Sentry.captureException(e, hint)
            }
        }
    }

    protected fun dataInitialized(): Boolean {
        return ::coverViewData.isInitialized
    }

    private fun onConnectionFailure() {
        if (!noConnectionShown) {
            fragment.lifecycleScope.launch {
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
            issueLiveData?.removeObserver(::issueObserver)
            issueLiveData = contentService
                .getCacheStatusFlow(coverViewData.issueKey)
                .asLiveData()
                .also {
                    if (!shouldNotShowDownloadIcon) {
                        it.observe(fragment, ::issueObserver)
                    }
                }
        }
    }

    private fun issueObserver(update: CacheStateUpdate) {
        boundView?.setDownloadIconForStatus(
            update.cacheState
        )
        when (update.type) {
            CacheStateUpdate.Type.BAD_CONNECTION -> onConnectionFailure()
            else -> Unit
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