package de.taz.app.android.ui.pdfViewer

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RelativeLayout
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.artifex.mupdf.viewer.MuPDFCore
import com.artifex.mupdf.viewer.PageAdapter
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.launch


class PdfRenderFragment(val position: Int) : BaseMainFragment(R.layout.fragment_pdf_render) {

    val log by Log
    private lateinit var pdfReaderView: MuPDFReaderView
    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()

    private val issueContentViewModel: IssueViewerViewModel by lazy {
        ViewModelProvider(
            requireActivity(), SavedStateViewModelFactory(
                requireActivity().application, requireActivity()
            )
        ).get(IssueViewerViewModel::class.java)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val muPdfWrapper = view.findViewById<RelativeLayout>(R.id.mu_pdf_wrapper)
        pdfPagerViewModel.pdfDataList.observe(viewLifecycleOwner) { pageList ->
            val pdfPage = pageList[position].pdfFile
            val core = MuPDFCore(pdfPage.path)
            pdfReaderView = MuPDFReaderView(context)
            pdfReaderView.adapter = PageAdapter(context, core)
            pdfReaderView.clickCoordinatesListener = { coordinates ->
                showFramesIfPossible(coordinates.first, coordinates.second)
            }
            pdfReaderView.onBorderListener = { border ->
                // TODO -> this could be != ViewBorder.NONE to determin that view is on one border
                // but atm it is then kinda flicky
                pdfPagerViewModel.setUserInputEnabled(border == ViewBorder.BOTH)
            }
            pdfReaderView.onScaleListener = { pinchOut ->
                if (pinchOut) {
                    pdfPagerViewModel.hideDrawerLogo.postValue(!pinchOut)
                }
            }
            muPdfWrapper.addView(pdfReaderView)
        }
    }

    private fun showFramesIfPossible(x: Float, y: Float) {
        log.verbose("Clicked on x: $x, y:$y")
        pdfPagerViewModel.pdfDataList.observe(viewLifecycleOwner) { list ->
            val frameList = list[position].frameList
            val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
            frame?.let {
                it.link?.let { link ->
                    if (link.startsWith("art") && link.endsWith(".html")) {
                        lifecycleScope.launch {
                            pdfPagerViewModel.hideDrawerLogo.postValue(false)
                            requireActivity().supportFragmentManager.beginTransaction()
                                .add(
                                    R.id.activity_pdf_fragment_placeholder,
                                    ArticlePagerFragment(),
                                    "IN_ARTICLE"
                                )
                                .addToBackStack(null)
                                .commit()
                            issueContentViewModel.setDisplayable(
                                pdfPagerViewModel.issueKey,
                                link
                            )
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
}
