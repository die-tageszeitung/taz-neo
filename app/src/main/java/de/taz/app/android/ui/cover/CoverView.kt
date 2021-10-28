package de.taz.app.android.ui.cover

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.RelativeLayout
import com.bumptech.glide.RequestManager
import de.taz.app.android.R
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.monkey.getColorFromAttr
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.home.page.CoverType
import de.taz.app.android.ui.home.page.CoverViewData
import de.taz.app.android.ui.home.page.MomentWebView
import kotlinx.android.synthetic.main.view_cover.view.*


const val MOMENT_FADE_DURATION_MS = 500L
private const val LOADING_FADE_OUT_DURATION_MS = 500L


/**
 * View to show a Cover/Moment in
 * Depending on [CoverViewData.momentType] it will either create an ImageView or a WebView
 */
@SuppressLint("ClickableViewAccessibility")
class CoverView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    var shouldNotShowDownloadIcon: Boolean = false
        private set

    private var momentElevation: Float? = null
    private var downloadButtonListener: ((View) -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_cover, this, true)

        // remove and store elevation, so we can restore it once the loading screen is hidden
        // otherwise the loading screen will have a shadow what is not wanted
        momentElevation = moment_container.elevation
        moment_container.elevation = 0f

        // if provided set [shouldNotShowDownloadIcon] if it is not provided downloads icons
        // will be shown
        attrs?.let {
            val styledAttributes = context.obtainStyledAttributes(attrs, R.styleable.CoverView)
            shouldNotShowDownloadIcon = styledAttributes.getBoolean(
                R.styleable.CoverView_do_not_show_download_icon,
                false
            )
            view_moment_download_icon_wrapper.visibility =
                if (shouldNotShowDownloadIcon) View.GONE else View.VISIBLE
            styledAttributes.recycle()
        }
    }

    // reset all data
    fun clear() {
        cover_placeholder.removeAllViews()
        clearDate()
        hideDownloadIcon()
        showLoadingScreen()
    }

    private fun clearDate() {
        fragment_moment_date.text = ""
    }

    /**
     * Show the Cover/Moment
     * @param data - the data necessary to show it
     * @param dateFormat    the format the date should be shown in
     *                      defaults to [DateFormat.LongWithoutWeekDay]
     * @param glideRequestManager the glideRequestManager to use when showing images
     */
    fun show(
        data: CoverViewData,
        dateFormat: DateFormat = DateFormat.LongWithoutWeekDay,
        glideRequestManager: RequestManager
    ) {
        showLoadingScreen()
        data.momentUri?.let {
            showCover(it, data.momentType, glideRequestManager)
        }
        setDownloadIconForStatus(data.downloadStatus)
        setDate(data.issueKey.date, dateFormat)
    }

    /**
     * set the DownloadStatus of this view
     */
    fun setDownloadIconForStatus(downloadStatus: DownloadStatus) {
        when (downloadStatus) {
            DownloadStatus.done -> hideDownloadIcon(true)
            DownloadStatus.started -> showLoadingIcon()
            else -> showDownloadIcon()
        }
    }

    // catch a long click on the container
    override fun setOnLongClickListener(l: OnLongClickListener?) {
        moment_container.setOnLongClickListener(l)
    }

    /**
     * show given date or hide date altogether
     */
    private fun setDate(date: String?, dateFormat: DateFormat) {
        if (date !== null) {
            when (dateFormat) {
                DateFormat.LongWithWeekDay ->
                    fragment_moment_date.text = DateHelper.stringToLongLocalizedString(date)
                DateFormat.LongWithoutWeekDay ->
                    fragment_moment_date.text = DateHelper.stringToMediumLocalizedString(date)
                DateFormat.None ->
                    fragment_moment_date.visibility = View.GONE
            }
        } else {
            fragment_moment_date.visibility = View.GONE
        }
    }

    private fun showLoadingScreen() {
        cover_placeholder.removeAllViews()
        moment_container.elevation = 0f
        moment_progressbar.animate().alpha(1f).duration = LOADING_FADE_OUT_DURATION_MS
    }

    private fun hideLoadingScreen() {
        moment_progressbar.animate().alpha(0f).apply {
            duration = LOADING_FADE_OUT_DURATION_MS
        }.withEndAction {
            momentElevation?.let { moment_container.elevation = it }
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

    private fun activateDownloadButtonListener() {
        downloadButtonListener?.let {
            view_moment_download_icon_wrapper.setOnClickListener(it)
        }
    }

    private fun deactivateDownloadButtonListener() {
        view_moment_download_icon_wrapper.setOnLongClickListener(null)
    }

    private fun showDownloadIcon() {
        view_moment_downloading?.visibility = View.GONE
        view_moment_download_finished?.visibility = View.GONE
        view_moment_download?.visibility = View.VISIBLE
        activateDownloadButtonListener()
    }

    private fun hideDownloadIcon(fadeOutAnimation: Boolean = false) {
        val wasDownloading = view_moment_downloading?.visibility == View.VISIBLE
        view_moment_downloading?.visibility = View.GONE
        view_moment_download?.visibility = View.GONE
        view_moment_download_finished?.visibility = View.GONE
        deactivateDownloadButtonListener()

        if (wasDownloading && fadeOutAnimation) {
            view_moment_download_finished?.apply {
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
        view_moment_download?.visibility = View.GONE
        view_moment_download_finished?.visibility = View.GONE
        view_moment_downloading?.visibility = View.VISIBLE
        view_moment_download_icon_wrapper.setOnClickListener(null)
    }

    private fun showCover(
        uri: String,
        type: CoverType,
        glideRequestManager: RequestManager
    ) {
        cover_placeholder.removeAllViews()
        when (type) {
            CoverType.ANIMATED -> showAnimatedCover(uri)
            CoverType.FRONT_PAGE,
            CoverType.STATIC -> showStaticCover(uri, glideRequestManager)
        }
    }

    private fun showAnimatedCover(uri: String) {
        val webView = MomentWebView(context)
        cover_placeholder.addView(webView)

        // configure WebView
        webView.apply {
            outlineProvider = ViewOutlineProvider.PADDED_BOUNDS
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            setInitialScale(15)
            settings.apply {
                useWideViewPort = true
                loadWithOverviewMode = true
                loadsImagesAutomatically = true
                allowFileAccess = true
            }
            setBackgroundColor(context.getColorFromAttr(R.color.backgroundColor))
            loadUrl(uri)
            animate().alpha(1f).duration = MOMENT_FADE_DURATION_MS
        }
        hideLoadingScreen()
    }

    private fun showStaticCover(uri: String, glideRequestManager: RequestManager) {
            val imageView = ImageView(context).apply {
                layoutParams =
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }
            imageView.alpha = 0f
            cover_placeholder.addView(imageView)
            glideRequestManager
                .load(uri)
                .into(imageView)
            hideLoadingScreen()
            imageView.animate().alpha(1f).duration = MOMENT_FADE_DURATION_MS
        }

}
