package de.taz.app.android.ui.moment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.*
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.view_moment.view.*
import kotlinx.coroutines.*
import java.util.*


class MomentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private val log by Log

    private var issueDownloadStatusLiveData: LiveData<DownloadStatus>? = null
    var currentIssueStub: IssueStub? = null
    private var dateFormat: DateFormat? = null
    private var lifecycleOwner: LifecycleOwner? = context as? LifecycleOwner

    private val feedRepository = FeedRepository.getInstance(context.applicationContext)
    private val fileHelper = FileHelper.getInstance(context.applicationContext)
    private val issueRepository = IssueRepository.getInstance(context.applicationContext)
    private val momentRepository = MomentRepository.getInstance(context.applicationContext)
    private val dataService = DataService.getInstance(context.applicationContext)

    private var shouldNotShowDownloadIcon: Boolean = false

    private var displayJob: Job? = null
    private var downloadJob: Job? = null

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

    fun clear() {
        displayJob?.cancel()
        displayJob = null

        issueDownloadStatusLiveData = null
        currentIssueStub = null
        dateFormat = null
        clearDate()
        hideBitmap()
        hideDownloadIcon()
        showProgressBar()

        resetDownloadJob()
    }

    private fun resetDownloadJob() {
        downloadJob?.cancel()
        downloadJob = null
    }

    suspend fun displayIssue(issueStub: IssueStub, dateFormat: DateFormat? = null) {
        withContext(Dispatchers.Main) {
            clear()
        }
        currentIssueStub = issueStub

        withContext(Dispatchers.IO) {
            this@MomentView.dateFormat = dateFormat
            issueDownloadStatusLiveData = currentIssueStub?.let {
                dataService.getDownloadLiveData(it.getIssue())
            }

        }

        displayJob = lifecycleOwner?.lifecycleScope?.launchWhenResumed {
            launch {
                var feed: Feed?
                withContext(Dispatchers.IO) { feed = feedRepository.get(issueStub.feedName) }
                setDimension(feed)
            }

            launch { setDate(issueStub.date) }

            launch { hideOrShowDownloadIcon() }

            launch { showMoment(issueStub) }
        }
    }

    private suspend fun showMoment(issueOperations: IssueOperations) = withContext(Dispatchers.IO) {
        val moment = momentRepository.get(issueOperations)
        dataService.ensureDownloaded(moment)
        showMomentImage(moment)

    }

    private fun hideOrShowDownloadIcon() {
        if (!shouldNotShowDownloadIcon) {
            view_moment_download_icon_wrapper?.visibility = View.VISIBLE

            issueDownloadStatusLiveData!!.observeDistinct(lifecycleOwner!!) { downloadStatus ->
                when (downloadStatus) {
                    DownloadStatus.done ->
                        hideDownloadIcon()
                    DownloadStatus.started ->
                        showLoadingIcon()
                    else ->
                        showDownloadIcon()
                }
            }
        } else {
            view_moment_download_icon_wrapper?.visibility = View.GONE
        }
    }

    private fun clearDate() {
        fragment_moment_date.text = ""
    }

    private fun setDate(date: String?) {
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
        fragment_moment_image.apply {
            alpha = 0f
            setImageDrawable(null)
        }
    }

    private fun showMomentImage(moment: Moment) {
        lifecycleOwner?.lifecycleScope?.launch(Dispatchers.IO) {
            generateBitmapForMoment(moment)?.let {
                showBitmap(it)
            }
        }
    }

    private suspend fun showBitmap(bitmap: Bitmap) = withContext(Dispatchers.Main) {
        fragment_moment_image.apply {
            setImageBitmap(bitmap)
            animate().alpha(1f).duration = 100
            momentElevation?.let { fragment_moment_image.elevation = it }
        }
        hideProgressBar()
    }

    private fun showProgressBar() {
        fragment_moment_image.elevation = 0f
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
        }

    }

    private fun showDownloadIcon() {
        fragment_moment_downloading?.visibility = View.GONE
        fragment_moment_download_finished?.visibility = View.GONE
        fragment_moment_download?.visibility = View.VISIBLE

        view_moment_download_icon_wrapper?.setOnClickListener {
            showLoadingIcon()

            lifecycleOwner?.lifecycleScope?.launch(Dispatchers.IO) {
                currentIssueStub?.let { issueRepository.getIssue(it) }?.let { issue ->
                    // we refresh the issue from network, as the cache might be pretty stale at this point (issues might be edited after release)
                    val updatedIssue =
                        dataService.getIssue(issue.issueKey, allowCache = false)
                    currentIssueStub = IssueStub(updatedIssue)
                    // If the issue from database is older that the refreshed one
                    if (
                        Date(updatedIssue.moTime.toLong()) >
                        Date(issue.moTime.toLong())
                    ) {
                        dataService.ensureDownloaded(updatedIssue)
                    } else {
                        dataService.ensureDownloaded(issue)
                    }
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

    private suspend fun generateBitmapForMoment(moment: Moment): Bitmap? {
        return moment.getMomentImage()?.let {
            val file = fileHelper.getFile(it)
            if (!file.exists()) {
                log.warn("imgFile of $moment does not exist, redownloading")

                lifecycleOwner?.lifecycleScope?.launch(Dispatchers.IO) {
                    dataService.ensureDownloaded(moment)
                }
            }
            // scale image to reduce memory costs
            val bitmapOptions = BitmapFactory.Options()
            bitmapOptions.inSampleSize = (4 / resources.displayMetrics.density).toInt()
            BitmapFactory.decodeFile(file.absolutePath, bitmapOptions)
        }
    }
}
