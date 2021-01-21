package de.taz.app.android.ui.pdfViewer

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.artifex.mupdf.viewer.ReaderView
import de.taz.app.android.R
import de.taz.app.android.api.models.Frame
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.ui.issueViewer.IssueViewerActivity.Companion.KEY_FINISH_ON_BACK_PRESSED
import de.taz.app.android.ui.issueViewer.IssueViewerActivity.Companion.KEY_DISPLAYABLE
import de.taz.app.android.ui.issueViewer.IssueViewerActivity.Companion.KEY_ISSUE_KEY
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import io.sentry.core.Sentry


@SuppressLint("ViewConstructor")
class MuPDFReaderView constructor(
    context: Context?,
    frameList: List<Frame>,
    issueKey: IssueKey,
    viewPager2: ViewPager2
) : ReaderView(
    context
) {
    private val log by Log
    var frameList: List<Frame> = emptyList()
    var issueKey: IssueKey
    private var viewPager2: ViewPager2
    var scrollToLeft = false

    init {
        this.frameList = frameList
        this.issueKey = issueKey
        this.viewPager2 = viewPager2
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        showFrameIfPossible(displayedView, e.x, e.y)
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        // only allow view pagers swiping when on boarder
        viewPager2.isUserInputEnabled = (displayedView.left >= 0 && scrollToLeft) ||
                displayedView.right <= width && !scrollToLeft
        return super.onInterceptTouchEvent(ev)
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        scrollToLeft = distanceX <= 0f
        return super.onScroll(e1, e2, distanceX, distanceY)
    }

    /**
     * Calculates the click coordinates of a potentially zoomed in and scrolled part to the total
     * view. With that coordinates we can check if they are in any of the given frames.
     * If so, the according article will be shown.
     */
    private fun showFrameIfPossible(view: View, clickedX: Float, clickedY: Float) {

        // Cet the scale factor from dividing total image by viewed part (eg. 2.0):
        val scaleX: Float = view.width / width.toFloat()
        val scaleY: Float = view.height / height.toFloat()

        // Calculate the relatively clicked coordinates by dividing the scale factor (e.g. 200):
        val relClickedX = clickedX / scaleX
        val relClickedY = clickedY / scaleY

        // Get the missed part of the total image (eg. zoomed & scrolled in the middle: -600):
        val missedFromZoomX = view.left / scaleX
        val missedFromZoomY = view.top / scaleY

        // Sum up to get the real click coordinates of the total image (e.g. 200 - (-600) = 800):
        val calculatedX = relClickedX - missedFromZoomX
        val calculatedY = relClickedY - missedFromZoomY

        // Calculate the ratio (e.g. 800 / 1080 = 0.7407):
        x = calculatedX / width.toFloat()
        y = calculatedY / height.toFloat()

        log.debug("Clicked on x: $x, y:$y [scale: $scaleX]")
        val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
        log.debug("found frame with link: ${frame?.link}")
        frame?.let {
            if (it.link?.startsWith("art") == true && it.link.endsWith(".html")) {
                Intent(context, IssueViewerActivity::class.java).apply {
                    putExtra(KEY_FINISH_ON_BACK_PRESSED, true)
                    putExtra(KEY_ISSUE_KEY, issueKey)
                    putExtra(KEY_DISPLAYABLE, it.link)
                    context?.startActivity(this)
                }
            } else if (it.link?.startsWith("http") == true || it.link?.startsWith("mailto:") == true) {
                    openExternally(it.link)
            } else if (it.link?.startsWith("s") == true && it.link.endsWith(".pdf")) {
                // TODO open pdfPage
           /*     viewPager2.setCurrentItem(
                    getPositionOfPdf(it.link)
                )*/
            } else {
                val hint = "Don't know how to open ${it.link}"
                log.warn(hint)
                Sentry.captureMessage(hint)
            }
        } ?: run {
            frameList.forEach {
                log.verbose("possible frame: $it")
            }
        }
    }
    private fun openExternally(url: String) {
        val color = ContextCompat.getColor(context, R.color.colorAccent)
        try {
            CustomTabsIntent.Builder().setToolbarColor(color).build().apply {
                launchUrl(context, Uri.parse(url))
            }
        } catch (e: ActivityNotFoundException) {
            val toastHelper =
                ToastHelper.getInstance(context)
            if (url.startsWith("mailto:")) {
                toastHelper.showToast(R.string.toast_no_email_client)
            } else {
                toastHelper.showToast(R.string.toast_unknown_error)
            }
        }

    }
}