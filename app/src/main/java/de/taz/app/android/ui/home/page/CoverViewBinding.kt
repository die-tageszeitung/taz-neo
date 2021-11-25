package de.taz.app.android.ui.home.page

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.RequestManager
import de.taz.app.android.R
import de.taz.app.android.content.ContentService
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.cover.CoverView
import de.taz.app.android.ui.home.page.coverflow.DownloadObserver
import io.sentry.Sentry
import kotlinx.coroutines.*

interface CoverViewActionListener {
    fun onLongClicked(coverPublication: AbstractCoverPublication) = Unit
    fun onImageClicked(coverPublication: AbstractCoverPublication) = Unit
    fun onDateClicked(coverPublication: AbstractCoverPublication) = Unit
}

class CoverBindingException(
    message: String = "Binding cover data failed",
    cause: Exception?
) : Exception(message, cause)


abstract class CoverViewBinding(
    private val fragment: Fragment,
    protected val coverPublication: AbstractCoverPublication,
    private val dateFormat: DateFormat,
    private val glideRequestManager: RequestManager,
    private val onMomentViewActionListener: CoverViewActionListener
) {
    private var boundView: CoverView? = null
    private lateinit var coverViewData: CoverViewData

    protected val applicationContext: Context = fragment.requireContext().applicationContext

    private val toastHelper = ToastHelper.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)
    private var bindJob: Job? = null

    private var downloadObserver: DownloadObserver? = null

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

    private suspend fun bindView(view: CoverView) = withContext(Dispatchers.Main) {
        boundView = view.apply {
            show(coverViewData, coverPublication.date, dateFormat, glideRequestManager)

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
                is FrontpagePublication -> IssuePublicationWithPages(coverPublication.feedName, coverPublication.date)
                is MomentPublication -> IssuePublication(coverPublication.feedName, coverPublication.date)
                else -> throw IllegalStateException("Unknown publication type ${coverPublication::class.simpleName}")
            }

            downloadObserver = DownloadObserver(
                fragment,
                contentService,
                toastHelper,
                issuePublication,
                view.findViewById(R.id.view_moment_download),
                view.findViewById(R.id.view_moment_download_finished),
                view.findViewById(R.id.view_moment_downloading)
            ).also {
                it.startObserving()
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