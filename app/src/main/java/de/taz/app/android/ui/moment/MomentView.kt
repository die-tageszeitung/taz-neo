package de.taz.app.android.ui.moment

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.R
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.getColorFromAttr
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.view_moment.view.*
import kotlinx.coroutines.*

/**
 * TODO REFACTOR
 *
 * The practice handling loading state in this view leads to bad raceconditions that are
 * prevented with some flaky workarounds. In extreme situations broken images can be produced
 * This needs to be refactored while implementing a proper recyclerview with deferred loading
 * managing loading state and populating views. The view should not handle this
 */
class MomentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private val log by Log
    private var lifecycleOwner: LifecycleOwner? = context as? LifecycleOwner

    private val feedRepository = FeedRepository.getInstance(context.applicationContext)
    private val fileHelper = FileHelper.getInstance(context.applicationContext)
    private val momentRepository = MomentRepository.getInstance(context.applicationContext)
    private val dataService = DataService.getInstance(context.applicationContext)
    private val toastHelper = ToastHelper.getInstance(context.applicationContext)

    private var shouldNotShowDownloadIcon: Boolean = false

    private var currentIssueKey: IssueKey? = null

    private var displayJob: Job? = null
    private var issueDownloadStatusLiveData: LiveData<DownloadStatus>? = null
    private var issueDownloadObserver: Observer<DownloadStatus>? = null

    private var momentDownloadStatusLiveData: LiveData<DownloadStatus>? = null
    private var momentDownloadStatusObserver: Observer<DownloadStatus>? = null

    private var momentElevation: Float? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_moment, this, true)
        if (momentElevation == null) {
            momentElevation = fragment_moment_image.elevation
        }

        attrs?.let {
            val styledAttributes =
                getContext().obtainStyledAttributes(attrs, R.styleable.MomentView)
            val textAlign = styledAttributes.getInteger(
                R.styleable.MomentView_archive_item_text_orientation,
                View.TEXT_ALIGNMENT_CENTER
            )
            shouldNotShowDownloadIcon = styledAttributes.getBoolean(
                R.styleable.MomentView_do_not_show_download_icon,
                false
            )
            if (shouldNotShowDownloadIcon) {
                hideDownloadIcon()
            }
            styledAttributes.recycle()

            fragment_moment_date?.apply {
                textAlignment = textAlign
            }
        }
    }

    suspend fun clear() {
        displayJob?.cancelAndJoin()
        displayJob = null

        issueDownloadObserver?.let {
            issueDownloadStatusLiveData?.removeObserver(it)
        }
        issueDownloadObserver = null
        issueDownloadStatusLiveData = null

        momentDownloadStatusObserver?.let {
            momentDownloadStatusLiveData?.removeObserver(it)
        }
        momentDownloadStatusObserver = null
        momentDownloadStatusLiveData = null

        clearDate()
        hideBitmap()
        hideDownloadIcon()
        showProgressBar()

    }

    suspend fun displayIssue(issueStub: IssueStub, dateFormat: DateFormat? = null) {
        withContext(Dispatchers.Main) {
            clear()
            currentIssueKey = issueStub.issueKey
        }
        CoroutineScope(Dispatchers.IO).launch {
            dataService.ensureDownloaded(momentRepository.get(issueStub))
        }
        displayJob = lifecycleOwner?.lifecycleScope?.launch(Dispatchers.IO) {

            var feed: Feed?
            withContext(Dispatchers.IO) { feed = feedRepository.get(issueStub.feedName) }
            setDimension(feed)


            withContext(Dispatchers.Main) {
                setDate(issueStub.date, dateFormat)
                hideOrShowDownloadIcon(issueStub)
            }
            showMoment(issueStub)
        }
        displayJob?.join()
    }

    private suspend fun showMoment(issueOperations: IssueOperations) = withContext(Dispatchers.IO) {
        val moment = momentRepository.get(issueOperations)
        showMomentImage(moment)
    }

    private suspend fun hideOrShowDownloadIcon(issueStub: IssueStub) {
        currentIssueKey = issueStub.issueKey
        if (!shouldNotShowDownloadIcon) {
            view_moment_download_icon_wrapper?.visibility = View.VISIBLE
            withContext(Dispatchers.IO) {
                dataService.withDownloadLiveData(issueStub) {
                    issueDownloadStatusLiveData = it
                    withContext(Dispatchers.Main) {
                        issueDownloadObserver =
                            issueDownloadStatusLiveData!!.observeDistinct(lifecycleOwner!!) { downloadStatus ->
                                // it is possible that this observer is executed after the view has been recycled and cleared (race condition)
                                // if we check if the issuekey is still the one the observer was setup with we can make sure
                                if (currentIssueKey != issueStub.issueKey) {
                                    log.warn("Race condition catched in MomentView")
                                    return@observeDistinct
                                }
                                when (downloadStatus) {
                                    DownloadStatus.done ->
                                        hideDownloadIcon()
                                    DownloadStatus.started ->
                                        showLoadingIcon()
                                    else ->
                                        showDownloadIcon(issueStub)
                                }
                            }
                    }
                }
            }

        } else {
            view_moment_download_icon_wrapper?.visibility = View.GONE
        }
    }

    private fun clearDate() {
        fragment_moment_date.text = ""
    }

    private fun setDate(date: String?, dateFormat: DateFormat?) {
        if (date !== null) {
            when (dateFormat) {
                DateFormat.LongWithWeekDay ->
                    fragment_moment_date.text = DateHelper.stringToLongLocalizedString(date)
                DateFormat.LongWithoutWeekDay ->
                    fragment_moment_date.text = DateHelper.stringToMediumLocalizedString(date)
                null ->
                    fragment_moment_date.visibility = View.GONE
            }
        } else {
            fragment_moment_date.visibility = View.GONE
        }
    }

    private fun hideBitmap() {
        fragment_moment_image.alpha = 0f
    }

    private fun showProgressBar() {
        fragment_moment_image.elevation = 0f
        fragment_moment_web_view.elevation = 0f
        fragment_moment_image_progressbar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        fragment_moment_image_progressbar.visibility = View.GONE
    }

    private fun setDimension(dimensionString: String) {
        lifecycleOwner?.lifecycleScope?.launch(Dispatchers.Main) {
            fragment_moment_image.apply {
                (layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = dimensionString
                requestLayout()
                forceLayout()
            }
            fragment_moment_web_view.apply {
                (layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = dimensionString
                requestLayout()
                forceLayout()
            }
        }

    }

    private fun showDownloadIcon(issueStub: IssueStub) {
        fragment_moment_downloading?.visibility = View.GONE
        fragment_moment_download_finished?.visibility = View.GONE
        fragment_moment_download?.visibility = View.VISIBLE

        view_moment_download_icon_wrapper?.setOnClickListener {
            showLoadingIcon()
            CoroutineScope(Dispatchers.IO).launch {
                val issue = issueStub.getIssue()
                // we refresh the issue from network, as the cache might be pretty stale at this point (issues might be edited after release)
                try {
                    val updatedIssue =
                        dataService.getIssue(
                            issue.issueKey,
                            allowCache = false,
                            saveOnlyIfNewerMoTime = true
                        )
                    updatedIssue?.let {
                        dataService.ensureDownloaded(updatedIssue)
                    }
                } catch (e: ConnectivityException.Recoverable) {
                    toastHelper.showNoConnectionToast()
                }
            }
        }

    }

    private fun hideDownloadIcon() {
        val wasDownloading = fragment_moment_downloading?.visibility == View.VISIBLE
        fragment_moment_downloading?.visibility = View.GONE
        fragment_moment_download?.visibility = View.GONE
        fragment_moment_download_finished?.visibility = View.GONE
        view_moment_download_icon_wrapper.setOnClickListener(null)

        if (wasDownloading) {
            fragment_moment_download_finished.alpha = 1f
            fragment_moment_download_finished?.visibility = View.VISIBLE
            lifecycleOwner?.lifecycleScope?.launchWhenResumed {
                delay(2000)
                fragment_moment_download_finished?.animate()?.alpha(0f)?.duration = 1000L
            }
        }
    }

    private fun showLoadingIcon() {
        fragment_moment_download?.visibility = View.GONE
        fragment_moment_download_finished?.visibility = View.GONE
        fragment_moment_downloading?.visibility = View.VISIBLE
        view_moment_download_icon_wrapper.setOnClickListener(null)
    }

    private suspend fun setDimension(feed: Feed?) = withContext(Dispatchers.Main) {
        val dimensionString = feed?.momentRatioAsDimensionRatioString() ?: DEFAULT_MOMENT_RATIO
        setDimension(dimensionString)
    }

    private suspend fun showMomentImage(moment: Moment) = withContext(Dispatchers.Main) {
        moment.getIndexHtmlForAnimated()?.let {fileEntry ->
            fragment_moment_image.visibility = GONE
            fragment_moment_web_view.visibility = VISIBLE
            showAnimatedImage(moment, fileEntry)
        } ?:  moment.getMomentImage()?.let { image ->
            fragment_moment_image.visibility = VISIBLE
            fragment_moment_web_view.visibility = GONE
            showStaticImage(moment, image)
        }
    }

    private fun showAnimatedImage(moment: Moment, fileEntry: FileEntry) {
        log.debug("show animated: ${fileEntry.path}")
        fileHelper.getFileDirectoryUrl(context).let { fileDir ->
            fragment_moment_web_view.apply {
                setInitialScale(30)
                settings.apply {
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    loadsImagesAutomatically = true
                }
                setBackgroundColor(context.getColorFromAttr(R.color.backgroundColor))
                loadUrl(fileDir+"/"+fileEntry.path)
            }
            hideProgressBar()
        }
    }

    private suspend fun showStaticImage(moment: Moment, image: Image) {
        val file = fileHelper.getFileByPath(image.path)
        momentElevation?.let { fragment_moment_image.elevation = it }
        withContext(Dispatchers.IO) {
            dataService.withDownloadLiveData(moment) {
                momentDownloadStatusLiveData = it
                withContext(Dispatchers.Main) {
                    momentDownloadStatusObserver =
                        momentDownloadStatusLiveData!!.observeDistinct(this@MomentView.lifecycleOwner!!) { downloadStatus ->
                            if (currentIssueKey != IssueKey(
                                    moment.issueFeedName,
                                    moment.issueDate,
                                    moment.issueStatus
                                )
                            ) {
                                return@observeDistinct
                            }
                            if (downloadStatus == DownloadStatus.done) {
                                Glide
                                    .with(context)
                                    .load(file)
                                    .centerInside()
                                    .into(fragment_moment_image)
                                    .clearOnDetach()
                                fragment_moment_image.animate().alpha(1f).duration = 100
                                hideProgressBar()
                            }
                        }
                }
            }
        }
    }

}
