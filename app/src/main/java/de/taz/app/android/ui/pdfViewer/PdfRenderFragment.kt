package de.taz.app.android.ui.pdfViewer

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.artifex.mupdf.viewer.MuPDFCore
import com.artifex.mupdf.viewer.PageAdapter
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.android.synthetic.main.fragment_pdf_render.*
import kotlinx.coroutines.launch


class PdfRenderFragment : BaseMainFragment(R.layout.fragment_pdf_render) {

    companion object {
        private const val KEY_POSITION = "KEY_POSITION"

        fun create(position: Int): PdfRenderFragment {
            val fragment = PdfRenderFragment()
            val args = Bundle()
            args.putInt(KEY_POSITION, position)
            fragment.arguments = args

            return fragment
        }
    }

    private val log by Log
    private var position: Int = 0
    private lateinit var pdfReaderView: MuPDFReaderView
    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()

    private val issueContentViewModel: IssueViewerViewModel by lazy {
        ViewModelProvider(
            requireActivity(), SavedStateViewModelFactory(
                requireActivity().application, requireActivity()
            )
        ).get(IssueViewerViewModel::class.java)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.getInt(KEY_POSITION, 0)?.let {
            position = it
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pdfReaderView = MuPDFReaderView(context)
        pdfPagerViewModel.pdfPageList.observe(viewLifecycleOwner) { pageList ->
            pdfReaderView.clickCoordinatesListener = { coordinates ->
                showFramesIfPossible(coordinates.first, coordinates.second)
            }
            pdfReaderView.onBorderListener = { border ->
                pdfPagerViewModel.setUserInputEnabled(border == ViewBorder.BOTH)
            }
            pdfReaderView.onScaleListener = { scaling ->
                pdfPagerViewModel.setRequestDisallowInterceptTouchEvent(scaling)
            }
            val pdfPage = pageList[position].pdfFile
            val core = MuPDFCore(pdfPage.path)
            pdfReaderView.adapter = PageAdapter(context, core)

            mu_pdf_wrapper?.addView(pdfReaderView)
        }
    }

    override fun onDestroyView() {
        pdfReaderView.adapter = null
        pdfReaderView.clickCoordinatesListener = null
        pdfReaderView.onBorderListener = null
        pdfReaderView.onScaleOutListener = null
        pdfReaderView.onScaleListener = null
        mu_pdf_wrapper?.removeAllViews()
        super.onDestroyView()
    }

    private fun showFramesIfPossible(x: Float, y: Float) {
        log.verbose("Clicked on x: $x, y:$y")
        val pageList = pdfPagerViewModel.pdfPageList.value
        pageList?.let { list ->
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
                                    ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
                                )
                                .addToBackStack(null)
                                .commit()
                            issueContentViewModel.setDisplayable(
                                IssueKey(pdfPagerViewModel.issueKey.value!!),
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
