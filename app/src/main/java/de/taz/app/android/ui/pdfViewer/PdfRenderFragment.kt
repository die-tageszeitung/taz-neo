package de.taz.app.android.ui.pdfViewer

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.RelativeLayout
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.artifex.mupdf.viewer.MuPDFCore
import com.artifex.mupdf.viewer.PageAdapter
import de.taz.app.android.R
import de.taz.app.android.api.models.Frame
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.ui.settings.SettingsActivity
import de.taz.app.android.util.Log
import io.sentry.core.Sentry
import java.io.File


class PdfRenderFragment : BaseMainFragment(R.layout.fragment_pdf_render) {

    val log by Log
    var pdfPage: File? = null
    var frameList: List<Frame> = emptyList()
    lateinit var pdfReaderView: MuPDFReaderView
    lateinit var issueKey: IssueKey
    lateinit var viewPager2: ViewPager2

    companion object {
        fun createInstance(
            pdfPageWithFrameList: Pair<File, List<Frame>>,
            issueKey: IssueKey,
            viewPager2: ViewPager2
        ): PdfRenderFragment {
            val fragment = PdfRenderFragment()
            fragment.pdfPage = pdfPageWithFrameList.first
            fragment.frameList = pdfPageWithFrameList.second
            fragment.issueKey = issueKey
            fragment.viewPager2 = viewPager2
            return fragment
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_pdf_render, container, false)
        val muPdfWrapper = view.findViewById<RelativeLayout>(R.id.mu_pdf_wrapper)
        pdfPage?.let {
            val core = MuPDFCore(it.path)
            pdfReaderView = MuPDFReaderView(context)
            pdfReaderView.adapter = PageAdapter(context, core)
            pdfReaderView.setOnTouchListener {_, event -> customSingleTapUpHandler(event)}
            muPdfWrapper.addView(pdfReaderView)
        }
        return view
    }

    private fun customSingleTapUpHandler(motionEvent: MotionEvent): Boolean {
        return if (pdfReaderView.singleTapDetector.onTouchEvent(motionEvent)) {
            val clickedPair = pdfReaderView.calculateClickCoordinates(motionEvent.x, motionEvent.y)
            showFramesIfPossible(clickedPair.first, clickedPair.second)
            true
        } else {
            viewPager2.isUserInputEnabled = pdfReaderView.onRightBoarder || pdfReaderView.onLeftBoarder
            false
        }
    }

    private fun showFramesIfPossible(x: Float, y: Float) {
        log.verbose("Clicked on x: $x, y:$y")
        val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
        frame?.let {
            it.link?.let { link ->
                if (link.startsWith("art") && link.endsWith(".html")) {
                    Intent(context, IssueViewerActivity::class.java).apply {
                        putExtra(IssueViewerActivity.KEY_FINISH_ON_BACK_PRESSED, true)
                        putExtra(IssueViewerActivity.KEY_ISSUE_KEY, issueKey)
                        putExtra(IssueViewerActivity.KEY_DISPLAYABLE, it.link)
                        context?.startActivity(this)
                    }
                } else if (link.startsWith("http") || link.startsWith("mailto:")) {
                    openExternally(it.link)
                } else if (link.startsWith("s") && link.endsWith(".pdf")) {
                    viewPager2.setCurrentItem(
                        (context as PdfPagerActivity).getPositionOfPdf(it.link)
                    )
                } else {
                    val hint = "Don't know how to open $link"
                    log.warn(hint)
                    Sentry.captureMessage(hint)
                }
            }
        } ?: run {
            frameList.forEach {
                log.debug("possible frame: $it")
            }
        }
    }

    private fun openExternally(url: String) {
        val color = ContextCompat.getColor(requireContext(), R.color.colorAccent)
        try {
            CustomTabsIntent.Builder().setToolbarColor(color).build().apply {
                launchUrl(requireContext(), Uri.parse(url))
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

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> requireActivity().finish()
            R.id.bottom_navigation_action_settings -> {
                Intent(requireActivity(), SettingsActivity::class.java).apply {
                    startActivity(this)
                }
            }
            R.id.bottom_navigation_action_help -> {
                Intent(requireActivity(), WelcomeActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                    startActivity(this)
                }
            }
        }
    }
}
