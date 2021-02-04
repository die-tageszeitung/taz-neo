package de.taz.app.android.ui.pdfViewer

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.RelativeLayout
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.artifex.mupdf.viewer.MuPDFCore
import com.artifex.mupdf.viewer.PageAdapter
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.WelcomeActivity
import de.taz.app.android.ui.issueViewer.IssueViewerActivity
import de.taz.app.android.ui.settings.SettingsActivity
import de.taz.app.android.util.Log
import io.sentry.core.Sentry


class PdfRenderFragment(val position: Int) : BaseMainFragment(R.layout.fragment_pdf_render) {

    val log by Log
    private lateinit var pdfReaderView: MuPDFReaderView
    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val muPdfWrapper = view.findViewById<RelativeLayout>(R.id.mu_pdf_wrapper)
        pdfPagerViewModel.pdfDataListModel.observe(viewLifecycleOwner) { pageList ->
            val pdfPage = pageList[position].pdfFile
            val core = MuPDFCore(pdfPage.path)
            pdfReaderView = MuPDFReaderView(context)
            pdfReaderView.adapter = PageAdapter(context, core)
            pdfReaderView.clickCoordinatesListener = { coordinates ->
                showFramesIfPossible(coordinates.first, coordinates.second)
            }
            pdfReaderView.onBorderListener = { border ->
                pdfPagerViewModel.toggleViewPagerInput(border != ViewBorder.NONE)
            }
            muPdfWrapper.addView(pdfReaderView)
        }
    }


    private fun showFramesIfPossible(x: Float, y: Float) {
        log.verbose("Clicked on x: $x, y:$y")
        pdfPagerViewModel.pdfDataListModel.observe(viewLifecycleOwner) { list ->
            val frameList = list[position].frameList
            val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
            frame?.let {
                it.link?.let { link ->
                    if (link.startsWith("art") && link.endsWith(".html")) {
                        Intent(context, IssueViewerActivity::class.java).apply {
                            putExtra(IssueViewerActivity.KEY_FINISH_ON_BACK_PRESSED, true)
                            putExtra(IssueViewerActivity.KEY_ISSUE_KEY, pdfPagerViewModel.issueKey)
                            putExtra(IssueViewerActivity.KEY_DISPLAYABLE, it.link)
                            context?.startActivity(this)
                        }
                    } else if (link.startsWith("http") || link.startsWith("mailto:")) {
                        openExternally(it.link)
                    } else if (link.startsWith("s") && link.endsWith(".pdf")) {
                        pdfPagerViewModel.goToPdfPage(it.link)
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
