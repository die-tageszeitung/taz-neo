package de.taz.app.android.ui.moment

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.RelativeLayout
import androidx.constraintlayout.widget.ConstraintLayout
import com.bumptech.glide.RequestManager
import de.taz.app.android.R
import de.taz.app.android.api.models.*
import de.taz.app.android.monkey.getColorFromAttr
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.home.page.MomentType
import de.taz.app.android.ui.home.page.MomentViewData
import de.taz.app.android.ui.pdfViewer.MuPDFThumbnail
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.view_moment.view.*


private const val MOMENT_FADE_DURATION_MS = 500L

@SuppressLint("ClickableViewAccessibility")
class MomentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {
    private val log by Log

    private var shouldNotShowDownloadIcon: Boolean = false
    private var momentElevation: Float? = null

    private var downloadButtonListener: ((View) -> Unit)? = null

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
                allowFileAccess = true
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

    fun clear(glideRequestManager: RequestManager) {
        glideRequestManager
            .clear(moment_image)

        moment_web_view.apply {
            loadUrl("about:blank")
            alpha = 0f
        }
        moment_web_view.visibility = View.GONE
        clearDate()
        hideDownloadIcon()
        showProgressBar()
    }

    fun show(
        data: MomentViewData,
        dateFormat: DateFormat? = DateFormat.LongWithoutWeekDay,
        glideRequestManager: RequestManager
    ) {
        data.momentUri?.let {
            showMomentImage(it, data.momentType, glideRequestManager)
        }
        setDownloadIconForStatus(data.downloadStatus)
        setDimension(data.dimension)
        setDate(data.issueKey.date, dateFormat)
    }

    fun resetDownloadIcon() {
        hideDownloadIcon(reset = true)
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
        downloadButtonListener = listener
    }

    fun setOnDateClickedListener(listener: ((View) -> Unit)?) {
        fragment_moment_date?.setOnClickListener(listener)
    }

    fun setOnImageClickListener(listener: ((View) -> Unit)?) {
        moment_container.setOnClickListener(listener)
    }

    fun activateDownloadButtonListener() {
        downloadButtonListener?.let {
            view_moment_download_icon_wrapper.setOnClickListener(it)
        }
    }

    private fun deactivateDownloadButtonListener() {
        view_moment_download_icon_wrapper.setOnLongClickListener(null)
    }

    private fun showDownloadIcon() {
        fragment_moment_downloading?.visibility = View.GONE
        fragment_moment_download_finished?.visibility = View.GONE
        fragment_moment_download?.visibility = View.VISIBLE
        activateDownloadButtonListener()
    }

    private fun hideDownloadIcon(reset: Boolean = false) {
        val wasDownloading = fragment_moment_downloading?.visibility == View.VISIBLE
        fragment_moment_downloading?.visibility = View.GONE
        fragment_moment_download?.visibility = View.GONE
        fragment_moment_download_finished?.visibility = View.GONE
        deactivateDownloadButtonListener()

        if (wasDownloading && !reset) {
            fragment_moment_download_finished?.apply {
                alpha = 1f
                visibility = View.VISIBLE
                animate().alpha(0f).apply {
                    duration = MOMENT_FADE_DURATION_MS
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

    private fun showMomentImage(
        uri: String,
        type: MomentType,
        glideRequestManager: RequestManager
    ) {
        when (type) {
            MomentType.ANIMATED -> {
                showAnimatedImage(uri)
                moment_web_view?.apply {
                    hideProgressBar()
                    animate().alpha(1f).duration = MOMENT_FADE_DURATION_MS
                }
            }
            MomentType.STATIC -> {
                showStaticImage(uri, glideRequestManager)
                moment_image?.apply {
                    hideProgressBar()
                    animate().alpha(1f).duration = MOMENT_FADE_DURATION_MS
                }
            }

            MomentType.PDF_FRONT_PAGE-> {
                val img = MuPDFThumbnail(uri).thumbnail(800)
                moment_image?.apply {
                    setImageBitmap(img)
                    hideProgressBar()
                    animate().alpha(1f).duration = MOMENT_FADE_DURATION_MS
                }
            }
        }
        momentElevation?.let { moment_container.elevation = it }
    }

    private fun showAnimatedImage(uri: String) {
        moment_web_view.visibility = View.VISIBLE
        moment_web_view.apply {

            loadUrl(uri)
        }
    }

    private fun showStaticImage(uri: String?, glideRequestManager: RequestManager) {
        glideRequestManager
            .load(uri)
            .into(moment_image)

        hideProgressBar()
    }
}
