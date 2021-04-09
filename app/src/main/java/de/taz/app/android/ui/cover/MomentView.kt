package de.taz.app.android.ui.cover

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.widget.ImageView
import com.bumptech.glide.RequestManager
import de.taz.app.android.R
import de.taz.app.android.monkey.getColorFromAttr
import de.taz.app.android.ui.home.page.CoverType
import de.taz.app.android.ui.home.page.MomentWebView
import kotlinx.android.synthetic.main.view_cover.view.*


@SuppressLint("ClickableViewAccessibility")
class MomentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CoverView(context, attrs, defStyleAttr) {
    private val momentImage: ImageView = ImageView(
        context,
    ).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }
    private val momentWebView: MomentWebView = MomentWebView(context).apply {
        outlineProvider = ViewOutlineProvider.PADDED_BOUNDS
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        alpha = 0f
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setInitialScale(15)
        setOnTouchListener { _, _ -> false }
        settings.apply {
            useWideViewPort = true
            loadWithOverviewMode = true
            loadsImagesAutomatically = true
            allowFileAccess = true
        }
        setBackgroundColor(context.getColorFromAttr(R.color.backgroundColor))
    }

    override fun clear(glideRequestManager: RequestManager) {
        cover_placeholder.removeAllViews()
        glideRequestManager
            .clear(momentImage)

        momentWebView.apply {
            loadUrl("about:blank")
        }
        clearDate()
        hideDownloadIcon()
        showProgressBar()
    }

    override fun showCover(
        uri: String,
        type: CoverType,
        glideRequestManager: RequestManager
    ) {
        cover_placeholder.removeAllViews()
        when (type) {
            CoverType.ANIMATED -> {
                momentWebView.alpha = 0f
                cover_placeholder.addView(momentWebView)
                showAnimatedImage(uri)
                momentWebView.animate().alpha(1f).duration = MOMENT_FADE_DURATION_MS
            }
            CoverType.STATIC -> {
                momentImage.alpha = 0f
                cover_placeholder.addView(momentImage)
                showStaticImage(uri, glideRequestManager)
                momentImage.animate().alpha(1f).duration = MOMENT_FADE_DURATION_MS
            }
            else -> throw IllegalStateException("MomentView only supports ANIMATED or STATIC CoverType")
        }
        hideProgressBar()
    }


    private fun showAnimatedImage(uri: String) {
        momentWebView.loadUrl(uri)
    }

    private fun showStaticImage(uri: String?, glideRequestManager: RequestManager) {
        glideRequestManager
            .load(uri)
            .into(momentImage)
        hideProgressBar()
    }
}
