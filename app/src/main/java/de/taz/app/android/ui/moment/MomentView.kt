package de.taz.app.android.ui.moment

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.Glide
import de.taz.app.android.R
import de.taz.app.android.api.models.*
import de.taz.app.android.monkey.getColorFromAttr
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.home.page.MomentType
import de.taz.app.android.ui.home.page.MomentViewData
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.view_moment.view.*

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

    private var shouldNotShowDownloadIcon: Boolean = false
    private var momentElevation: Float? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_moment, this, true)
        if (momentElevation == null) {
            momentElevation = moment_container.elevation
        }
        moment_container.elevation = 0f

        moment_image.apply {
            alpha = 0f
        }

        moment_web_view.apply {
            alpha = 0f
            setInitialScale(30)
            setOnTouchListener { _, _ -> false }
            settings.apply {
                useWideViewPort = true
                loadWithOverviewMode = true
                loadsImagesAutomatically = true
            }
            setBackgroundColor(context.getColorFromAttr(R.color.backgroundColor))
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
            view_moment_download_icon_wrapper.visibility =
                if (shouldNotShowDownloadIcon) View.GONE else View.VISIBLE
            styledAttributes.recycle()

            fragment_moment_date?.apply {
                textAlignment = textAlign
            }
        }
        showProgressBar()
    }

    fun clear() {
        Glide
            .with(this)
            .clear(moment_image)
        moment_web_view.apply {
            webViewClient = null
            loadUrl("about:blank")
            alpha = 0f
        }
        moment_web_view.apply {
            alpha = 0f
        }
        clearDate()
        hideDownloadIcon()
        showProgressBar()
    }

    fun show(data: MomentViewData, dateFormat: DateFormat? = DateFormat.LongWithoutWeekDay) {
        showMomentImage(data.momentUri, data.momentType)
        setDownloadIconForStatus(data.downloadStatus)
        setDimension(data.dimension)
        setDate(data.issueStub.date, dateFormat)
    }

    fun setDownloadIconForStatus(downloadStatus: DownloadStatus) {
        when (downloadStatus) {
            DownloadStatus.done -> hideDownloadIcon()
            DownloadStatus.started -> showLoadingIcon()
            else -> showDownloadIcon()
        }
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        moment_container.setOnLongClickListener(l)
    }

    private fun clearDate() {
        fragment_moment_date.text = ""
    }

    fun setDate(date: String?, dateFormat: DateFormat?) {
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

    private fun showProgressBar() {
        moment_container.elevation = 0f
        moment_image.alpha = 0f
        moment_web_view.alpha = 0f
        moment_progressbar.visibility = View.VISIBLE

    }

    private fun hideProgressBar() {
        moment_progressbar.visibility = View.GONE
    }

    private fun setDimension(dimensionString: String) {
        moment_container.apply {
            (layoutParams as ConstraintLayout.LayoutParams).dimensionRatio = dimensionString
            requestLayout()
        }
    }

    fun setOnDownloadClickedListener(listener: ((View) -> Unit)?) {
        view_moment_download_icon_wrapper?.setOnClickListener(listener)
    }

    fun setOnDateClickedListener(listener: ((View) -> Unit)?) {
        fragment_moment_date?.setOnClickListener(listener)
    }

    fun setOnImageClickListener(listener: ((View) -> Unit)?) {
        moment_container.setOnClickListener(listener)
    }

    private fun showDownloadIcon() {
        fragment_moment_downloading?.visibility = View.GONE
        fragment_moment_download_finished?.visibility = View.GONE
        fragment_moment_download?.visibility = View.VISIBLE
    }

    private fun hideDownloadIcon() {
        val wasDownloading = fragment_moment_downloading?.visibility == View.VISIBLE
        fragment_moment_downloading?.visibility = View.GONE
        fragment_moment_download?.visibility = View.GONE
        fragment_moment_download_finished?.visibility = View.GONE
        view_moment_download_icon_wrapper.setOnClickListener(null)

        if (wasDownloading) {
            fragment_moment_download_finished?.apply {
                alpha = 1f
                visibility = View.VISIBLE
                animate().alpha(0f).apply {
                    duration = 1000L
                    startDelay = 2000L
                }
            }
        }
    }

    private fun showLoadingIcon() {
        fragment_moment_download?.visibility = View.GONE
        fragment_moment_download_finished?.visibility = View.GONE
        fragment_moment_downloading?.visibility = View.VISIBLE
        view_moment_download_icon_wrapper.setOnClickListener(null)
    }

    private fun showMomentImage(uri: String?, type: MomentType) {
        when (type) {
            MomentType.ANIMATED -> {
                showAnimatedImage(uri)
                moment_web_view?.apply {
                    hideProgressBar()
                    animate().alpha(1f).duration = 1000L
                }
            }
            MomentType.STATIC -> {
                showStaticImage(uri)
                moment_image?.apply {
                    hideProgressBar()
                    animate().alpha(1f).duration = 1000L
                }
            }
        }
        momentElevation?.let { moment_container.elevation = it }
    }

    private fun showAnimatedImage(uri: String?) {
        moment_web_view.apply {
            loadUrl(uri)
        }

    }

    private fun showStaticImage(uri: String?) {
        Glide
            .with(this)
            .load(uri)
            .into(moment_image)

        hideProgressBar()
    }
}
