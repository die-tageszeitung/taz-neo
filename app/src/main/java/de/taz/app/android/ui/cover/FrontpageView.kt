package de.taz.app.android.ui.cover

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import com.bumptech.glide.RequestManager
import de.taz.app.android.ui.home.page.CoverType
import kotlinx.android.synthetic.main.view_cover.view.*


@SuppressLint("ClickableViewAccessibility")
class FrontpageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CoverView(context, attrs, defStyleAttr) {
    private val frontPageImage: ImageView = ImageView(
        context,
    ).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    override fun clear(glideRequestManager: RequestManager) {
        glideRequestManager
            .clear(frontPageImage)

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
            CoverType.FRONT_PAGE -> {
                frontPageImage.alpha = 0f
                cover_placeholder.addView(frontPageImage)
                glideRequestManager
                    .load(uri)
                    .into(frontPageImage)

                hideProgressBar()
                frontPageImage.animate().alpha(1f).duration = MOMENT_FADE_DURATION_MS
            }
            else -> throw IllegalStateException("FrontPageView only supports CoverType.FRONT_PAGE")
        }
        hideProgressBar()
    }
}
