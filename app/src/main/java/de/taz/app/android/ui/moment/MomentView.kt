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
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.Moment
import de.taz.app.android.download.DownloadService
import de.taz.app.android.monkey.observeDistinctUntil
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.MomentRepository
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.view_moment.view.*
import kotlinx.coroutines.*


class MomentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private val log by Log

    private var issueOperations: IssueOperations? = null
    private var dateFormat: DateFormat? = null
    private var lifecycleOwner: LifecycleOwner? = context as? LifecycleOwner

    private val dateHelper = DateHelper.getInstance(context.applicationContext)
    private val downloadService = DownloadService.getInstance(context.applicationContext)
    private val fileHelper = FileHelper.getInstance(context.applicationContext)
    private val issueRepository = IssueRepository.getInstance(context.applicationContext)
    private val momentRepository = MomentRepository.getInstance(context.applicationContext)

    private var shouldNotShowDownloadIcon: Boolean = false

    private var displayJob: Job? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_moment, this, true)

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

        issueOperations = null
        dateFormat = null
        clearDate()
        hideBitmap()
        hideDownloadIcon()
        showProgressBar()
    }

    fun displayIssue(issueOperations: IssueOperations, dateFormat: DateFormat? = null) {
        this.clear()

        this.dateFormat = dateFormat
        this.issueOperations = issueOperations

        displayJob = lifecycleOwner?.lifecycleScope?.launchWhenResumed {
            launch { setDimension(issueOperations.getFeed()) }

            launch { setDate(issueOperations.date) }

            launch { hideOrShowDownloadIcon() }

            launch { showMoment(issueOperations) }
        }
    }

    private suspend fun showMoment(issueOperations: IssueOperations) = withContext(Dispatchers.IO) {
        val moment = momentRepository.get(issueOperations)
        if (moment?.isDownloaded() == true) {
            showMomentImage(moment)
        } else {
            moment?.apply {
                download(context.applicationContext)
                withContext(Dispatchers.Main) {
                    isDownloadedLiveData().observeDistinctUntil(
                        lifecycleOwner = lifecycleOwner!!,
                        observationCallback = { isDownloaded ->
                            if (isDownloaded) {
                                showMomentImage(moment)
                            }
                        },
                        untilFunction = { isDownloaded -> isDownloaded }
                    )
                }
            }
        }
    }

    private fun hideOrShowDownloadIcon() {
        if (!shouldNotShowDownloadIcon) {
            issueOperations?.let { issueOperations ->
                val issueStubLiveData = issueRepository.getStubLiveData(
                    issueOperations.feedName, issueOperations.date, issueOperations.status
                )

                issueStubLiveData.observeDistinct(lifecycleOwner!!) { issueStub ->
                    if (issueStub?.dateDownload != null) {
                        hideDownloadIcon()
                    } else {
                        showDownloadIcon()
                    }
                }
            }
        }
    }

    private fun clearDate() {
        fragment_moment_date.text = ""
    }

    private fun setDate(date: String?) {
        if (date !== null) {
            when (dateFormat) {
                DateFormat.LongWithWeekDay ->
                    fragment_moment_date.text = dateHelper.stringToLongLocalizedString(date)
                DateFormat.LongWithoutWeekDay ->
                    fragment_moment_date.text =
                        dateHelper.stringToMediumLocalizedString(date)
                null ->
                    fragment_moment_date.visibility = View.GONE
            }
        } else {
            fragment_moment_date.visibility = View.GONE
        }
    }

    private fun hideBitmap() {
        fragment_moment_image.apply {
            visibility = View.INVISIBLE
            setImageResource(android.R.color.transparent)
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
            visibility = View.VISIBLE
        }
        hideProgressBar()
    }

    private fun showProgressBar() {
        fragment_moment_image_progressbar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        fragment_moment_image_progressbar.visibility = View.GONE
    }

    private fun setDimension(dimensionString: String) {
        lifecycleOwner?.lifecycleScope?.launch(Dispatchers.Main) {
            log.info("setting dimension to $dimensionString")
            fragment_moment_image.apply {
                (layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = dimensionString
                requestLayout()
                forceLayout()
            }
        }

    }

    private fun showDownloadIcon() {
        fragment_moment_is_downloaded?.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                lifecycleOwner?.lifecycleScope?.launch(Dispatchers.IO) {
                    issueOperations?.let {
                        issueRepository.getIssue(it)?.let { issue ->
                            downloadService.download(issue)
                        }
                    }
                }
            }
        }

    }

    private fun hideDownloadIcon() {
        fragment_moment_is_downloaded?.visibility = View.GONE
    }

    private suspend fun setDimension(feed: Feed?) = withContext(Dispatchers.Main) {
        val dimensionString = feed?.momentRatioAsDimensionRatioString() ?: DEFAULT_MOMENT_RATIO
        setDimension(dimensionString)
    }

    private fun generateBitmapForMoment(moment: Moment): Bitmap? {
        return moment.getMomentImage()?.let {
            val file = fileHelper.getFile(it)
            if (file.exists()) {
                // scale image to reduce memory costs
                val bitmapOptions = BitmapFactory.Options()
                bitmapOptions.inSampleSize = (4 / resources.displayMetrics.density).toInt()
                BitmapFactory.decodeFile(file.absolutePath, bitmapOptions)
            } else {
                log.error("imgFile of $moment does not exist")
                null
            }
        }
    }
}
