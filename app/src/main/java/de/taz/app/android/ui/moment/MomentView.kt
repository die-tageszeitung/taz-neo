package de.taz.app.android.ui.moment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.DEFAULT_MOMENT_RATIO
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.Moment
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.view_archive_item.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Exception


class MomentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private val log by Log

    private val viewModel = MomentViewModel()
    private var dateFormat: DateFormat? = null
    private val dateHelper: DateHelper = DateHelper.getInstance()
    private var lifecycleOwner: LifecycleOwner? = null

    private var shouldNotShowDownloadIcon: Boolean = false

    private var momentIsDownloadingObserver: Observer<Boolean>? = null
    private var momentIsDownloadedObserver: Observer<Boolean>? = null
    private var showDownloadIconObserver: Observer<Boolean>? = null

    init {
        inflate(context, R.layout.view_archive_item, this)

        attrs?.let {
            val styledAttributes =
                getContext().obtainStyledAttributes(attrs, R.styleable.MomentView)
            styledAttributes.getColor(
                R.styleable.MomentView_archive_item_text_color,
                Color.WHITE
            ).let {
                fragment_archive_moment_date.setTextColor(it)
            }

            styledAttributes.getInteger(
                R.styleable.MomentView_archive_item_text_orientation,
                View.TEXT_ALIGNMENT_CENTER
            ).let {
                fragment_archive_moment_date.textAlignment = it
            }

            shouldNotShowDownloadIcon = styledAttributes.getBoolean(
                R.styleable.MomentView_do_not_show_download_icon,
                false
            )
            styledAttributes.recycle()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lifecycleOwner = context as? LifecycleOwner ?: throw LifecycleOwnerNotFoundException()

        lifecycleOwner?.let { lifecycleOwner ->
            momentIsDownloadingObserver = viewModel.isMomentDownloadingLiveData
                .observeDistinct(lifecycleOwner) { isDownloading ->
                    if (!isDownloading) {
                        lifecycleOwner.lifecycleScope.launch { viewModel.moment?.download() }
                    }
                }

            momentIsDownloadedObserver = viewModel.isMomentDownloadedLiveData
                .observeDistinct(lifecycleOwner) { isDownloaded ->
                    if (isDownloaded) {
                        showMoment()
                    }
                }

        }
    }

    fun clear() {
        showDownloadIconObserver?.let {
            viewModel.moment?.isDownloadedLiveData()?.removeObserver(it)
            showDownloadIconObserver = null
        }
        momentIsDownloadingObserver?.let {
            viewModel.isMomentDownloadingLiveData.removeObserver(it)
            momentIsDownloadingObserver = null
        }
        momentIsDownloadedObserver?.let {
            viewModel.isMomentDownloadedLiveData.removeObserver(it)
            momentIsDownloadedObserver = null
        }
        viewModel.setIssueOperations(null)
        clearDate()
        hideBitmap()
        hideDownloadIcon()
        showProgressBar()
    }

    private fun showMoment() {
        lifecycleOwner?.lifecycleScope?.launch {
            setDate(viewModel.date)
            viewModel.moment?.let { showMomentImage(it) }
            if (!shouldNotShowDownloadIcon) {
                showDownloadIconObserver =
                    viewModel.isDownloadedLiveData.observeDistinct(lifecycleOwner!!) { isDownloaded ->
                        if (isDownloaded) {
                            hideDownloadIcon()
                        } else {
                            showDownloadIcon()
                        }
                    }
            }
        }
    }

    fun displayIssue(issueOperations: IssueOperations, dateFormat: DateFormat? = null) {
        this.clear()
        this.dateFormat = dateFormat
        viewModel.setIssueOperations(issueOperations)

        lifecycleOwner?.lifecycleScope?.launch(Dispatchers.IO) {
            val feed = issueOperations.getFeed()
            setDimension(feed)
        }
    }

    private fun clearDate() {
        fragment_archive_moment_date.text = ""
    }

    private fun setDate(date: String?) {
        if (date !== null) {
            when (dateFormat) {
                DateFormat.LongWithWeekDay ->
                    fragment_archive_moment_date.text = dateHelper.stringToLongLocalizedString(date)
                DateFormat.LongWithoutWeekDay ->
                    fragment_archive_moment_date.text =
                        dateHelper.stringToMediumLocalizedString(date)
                null ->
                    fragment_archive_moment_date.visibility = View.GONE
            }
        } else {
            fragment_archive_moment_date.visibility = View.GONE
        }
    }

    private fun hideBitmap() {
        fragment_archive_moment_image.apply {
            visibility = View.INVISIBLE
            setImageResource(android.R.color.transparent)
        }
    }

    private suspend fun showMomentImage(moment: Moment) = withContext(Dispatchers.IO) {
        generateBitmapForMoment(moment)?.let {
            showBitmap(it)
        }
    }

    private suspend fun showBitmap(bitmap: Bitmap) = withContext(Dispatchers.Main) {
        fragment_archive_moment_image.apply {
            setImageBitmap(bitmap)
            visibility = View.VISIBLE
        }
        hideProgressBar()
    }

    private fun showProgressBar() {
        fragment_archive_moment_image_progressbar.visibility = View.VISIBLE
    }

    private fun hideProgressBar() {
        fragment_archive_moment_image_progressbar.visibility = View.GONE
    }

    private fun setDimension(dimensionString: String) {
        lifecycleOwner?.lifecycleScope?.launch(Dispatchers.Main) {
            log.info("setting dimension to $dimensionString")
            fragment_archive_item_centered.apply {
                (layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = dimensionString
                requestLayout()
                forceLayout()
            }
        }

    }

    private fun showDownloadIcon() {
        fragment_archive_moment_is_downloaded?.visibility = View.VISIBLE
    }

    private fun hideDownloadIcon() {
        fragment_archive_moment_is_downloaded?.visibility = View.GONE
    }

    private fun setDimension(feed: Feed?) {
        val dimensionString = feed?.momentRatioAsDimensionRatioString() ?: DEFAULT_MOMENT_RATIO
        setDimension(dimensionString)
    }

    private fun generateBitmapForMoment(moment: Moment): Bitmap? {
        moment.getMomentImage().let {
            val file = FileHelper.getInstance().getFile(it)
            if (file.exists()) {
                return BitmapFactory.decodeFile(file.absolutePath)
            } else {
                log.error("imgFile of $moment does not exist")
            }
        }
        return null
    }
}

class LifecycleOwnerNotFoundException : Exception("no lifecycle owner given")