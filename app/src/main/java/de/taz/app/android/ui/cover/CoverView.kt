package de.taz.app.android.ui.cover

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.bumptech.glide.RequestManager
import com.bumptech.glide.signature.EmptySignature
import com.bumptech.glide.signature.ObjectKey
import de.taz.app.android.R
import de.taz.app.android.ui.home.page.CoverType
import de.taz.app.android.ui.home.page.CoverViewData
import de.taz.app.android.ui.home.page.CoverViewDate
import de.taz.app.android.ui.home.page.MomentWebView
import java.util.Date


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

    // region views
    init {
        // Inflate the layout first, so that we can find the views below
        LayoutInflater.from(context).inflate(R.layout.view_cover, this, true)
    }

    private val coverPlaceholder = findViewById<FrameLayout>(R.id.cover_placeholder)
    private val momentContainer = findViewById<ConstraintLayout>(R.id.moment_container)
    private val dateDownLoadWrapper = findViewById<ConstraintLayout>(R.id.view_cover_date_download_wrapper)
    private val momentDate = findViewById<TextView>(R.id.fragment_moment_date)
    private val momentProgressbar = findViewById<ProgressBar>(R.id.moment_progressbar)
    private val viewMomentDownloadIconWrapper =
        findViewById<ConstraintLayout>(R.id.view_moment_download_icon_wrapper)
    private val viewMomentDownload = findViewById<ImageView>(R.id.view_moment_download)
    private val viewMomentDownloadFinished =
        findViewById<ImageView>(R.id.view_moment_download_finished)
    private val viewMomentDownloading = findViewById<ProgressBar>(R.id.view_moment_downloading)
    // endregion

    init {
        // remove and store elevation, so we can restore it once the loading screen is hidden
        // otherwise the loading screen will have a shadow what is not wanted
        momentElevation = momentContainer.elevation
        momentContainer.elevation = 0f

        // if provided set [shouldNotShowDownloadIcon] if it is not provided downloads icons
        // will be shown
        attrs?.let {
            val styledAttributes = context.obtainStyledAttributes(attrs, R.styleable.CoverView)
            shouldNotShowDownloadIcon = styledAttributes.getBoolean(
                R.styleable.CoverView_do_not_show_download_icon,
                false
            )
            viewMomentDownloadIconWrapper.visibility =
                if (shouldNotShowDownloadIcon) View.GONE else View.VISIBLE
            styledAttributes.recycle()
        }
    }

    // reset all data
    fun clear() {
        coverPlaceholder.removeAllViews()
        clearDate()
        hideDownloadIcon()
        showLoadingScreen()
    }

    /**
     * Show the Cover/Moment
     * @param coverViewDate the formatted date string to show.
     *                      if null the date text input will not be shown at all (GONE)
     * @param glideRequestManager the glideRequestManager to use when showing images
     */
    fun show(
        data: CoverViewData,
        coverViewDate: CoverViewDate?,
        glideRequestManager: RequestManager
    ) {
        showLoadingScreen()
        data.momentUri?.let {
            showCover(it, data.momentType, data.dateDownloaded, glideRequestManager)
        }
        setDate(coverViewDate)
    }

    // catch a long click on the container
    override fun setOnLongClickListener(l: OnLongClickListener?) {
        momentContainer.setOnLongClickListener(l)
    }

    /**
     * show given date or hide date altogether
     */
    private fun setDate(coverViewDate: CoverViewDate?) {
        if (coverViewDate !== null) {
            // All the items in the recyclerview grid should have the same width,
            // thus we can simply check here which date to use, as it will be the same on all views.
            val useShortDate =
                width < context.resources.getDimension(R.dimen.fragment_cover_flow_min_width_long_date)

            momentDate.text = coverViewDate.dateStringShort
                ?.takeIf { useShortDate }
                ?: coverViewDate.dateString
            dateDownLoadWrapper.visibility = View.VISIBLE
        } else {
            dateDownLoadWrapper.visibility = View.GONE
        }
    }

    private fun clearDate() {
        momentDate.apply {
            visibility = View.VISIBLE
            text = ""
        }
    }

    private fun showLoadingScreen() {
        coverPlaceholder.removeAllViews()
        momentContainer.elevation = 0f
        momentProgressbar.animate().alpha(1f).duration = LOADING_FADE_OUT_DURATION_MS
    }

    private fun hideLoadingScreen() {
        momentProgressbar.animate().alpha(0f).apply {
            duration = LOADING_FADE_OUT_DURATION_MS
        }.withEndAction {
            momentElevation?.let { momentContainer.elevation = it }
        }
    }

    fun setOnDateClickedListener(listener: ((View) -> Unit)?) {
        momentDate?.setOnClickListener(listener)
    }

    fun setOnImageClickListener(listener: ((View) -> Unit)?) {
        momentContainer.setOnClickListener(listener)
    }

    private fun deactivateDownloadButtonListener() {
        viewMomentDownloadIconWrapper.setOnLongClickListener(null)
    }

    private fun hideDownloadIcon(fadeOutAnimation: Boolean = false) {
        val wasDownloading = viewMomentDownloading?.visibility == View.VISIBLE
        viewMomentDownloading?.visibility = View.GONE
        viewMomentDownload?.visibility = View.GONE
        viewMomentDownloadFinished?.visibility = View.GONE
        deactivateDownloadButtonListener()

        if (wasDownloading && fadeOutAnimation) {
            viewMomentDownloadFinished?.apply {
                alpha = 1f
                visibility = View.VISIBLE
                animate().alpha(0f).apply {
                    duration = MOMENT_FADE_DURATION_MS
                    startDelay = 2000L
                }
            }
        }
    }

    private fun showCover(
        uri: String,
        type: CoverType,
        dateDownloaded: Date?,
        glideRequestManager: RequestManager
    ) {
        coverPlaceholder.removeAllViews()
        when (type) {
            CoverType.ANIMATED -> showAnimatedCover(uri)
            CoverType.FRONT_PAGE,
            CoverType.STATIC -> showStaticCover(uri, dateDownloaded, glideRequestManager)
        }
    }

    private fun showAnimatedCover(uri: String) {
        val webView = MomentWebView(context)
        coverPlaceholder.addView(webView)

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
            setBackgroundColor(ContextCompat.getColor(context, R.color.backgroundColor))
            loadUrl(uri)
            animate().alpha(1f).duration = MOMENT_FADE_DURATION_MS
        }
        hideLoadingScreen()
    }

    private fun showStaticCover(uri: String, dateDownloaded: Date?, glideRequestManager: RequestManager) {
        val imageView = ImageView(context).apply {
            layoutParams =
                LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        imageView.alpha = 0f
        coverPlaceholder.addView(imageView)
        val signature = dateDownloaded
            ?.let { ObjectKey(it.time) }
            ?: EmptySignature.obtain()
        glideRequestManager
            .load(uri)
            .signature(signature)
            .into(imageView)
        hideLoadingScreen()
        imageView.animate().alpha(1f).duration = MOMENT_FADE_DURATION_MS
    }

}
