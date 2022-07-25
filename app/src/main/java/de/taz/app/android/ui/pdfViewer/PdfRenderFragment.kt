package de.taz.app.android.ui.pdfViewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.browser.customtabs.CustomTabColorSchemeParams
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
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageType
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentPdfRenderBinding
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.KeepScreenOnHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.webview.ImprintWebViewFragment
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.util.Log
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.NullPointerException


class PdfRenderFragment : BaseMainFragment<FragmentPdfRenderBinding>() {

    private var page: Page? = null

    companion object {
        private const val PAGE_NAME = "PAGE_NAME"

        fun create(page: Page): PdfRenderFragment {
            val fragment = PdfRenderFragment()
            fragment.page = page
            fragment.arguments = Bundle().also { it.putString(PAGE_NAME, page.pagePdf.name) }
            return fragment
        }
    }

    private val log by Log
    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()

    private var pdfReaderView: MuPDFReaderView? = null
    private lateinit var issueKey: IssueKeyWithPages
    private lateinit var articleRepository: ArticleRepository
    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private lateinit var keepScreenOnHelper: KeepScreenOnHelper

    private val storageService by lazy {
        StorageService.getInstance(requireContext().applicationContext)
    }

    private val pageRepository by lazy {
        PageRepository.getInstance(requireContext().applicationContext)
    }

    private val toastHelper by lazy {
        ToastHelper.getInstance(requireContext().applicationContext)
    }

    private val issueContentViewModel: IssueViewerViewModel by lazy {
        ViewModelProvider(
            requireActivity(), SavedStateViewModelFactory(
                requireActivity().application, requireActivity()
            )
        ).get(IssueViewerViewModel::class.java)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        articleRepository = ArticleRepository.getInstance(context)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(requireContext().applicationContext)
        keepScreenOnHelper = KeepScreenOnHelper.getInstance(requireContext().applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // if page is null get Page from DB and PAGE_NAME argument
        if (page == null) {
            arguments?.getString(PAGE_NAME)?.let {
                try {
                    lifecycleScope.launch(Dispatchers.IO) {
                        page = pageRepository.get(it)
                        withContext(Dispatchers.Main) { initializeThePageAdapter() }
                    }
                } catch (nfe: NotFoundException) {
                    log.error("Created with PAGE_NAME set")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        pdfPagerViewModel.issueKey.observe(this) {
            issueKey = it
        }

        lifecycleScope.launchWhenResumed {
            tazApiCssDataStore.keepScreenOn.asFlow().collect {
                keepScreenOnHelper.toggleScreenOn(it, activity)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pdfReaderView = MuPDFReaderView(requireContext())
        pdfReaderView?.apply {
            clickCoordinatesListener = { coordinates ->
                showFramesIfPossible(coordinates.first, coordinates.second)
            }
            onBorderListener = { border ->
                pdfPagerViewModel.setUserInputEnabled(border == ViewBorder.BOTH)
            }
            onScaleListener = { scaling ->
                pdfPagerViewModel.setRequestDisallowInterceptTouchEvent(scaling)
            }
            onSwipeListener = { swipeEvent ->
                lifecycleScope.launch {
                    pdfPagerViewModel.swipePageFlow.emit(swipeEvent)
                }
            }
        }
        initializeThePageAdapter()
    }

    private fun initializeThePageAdapter() {
        page?.pagePdf?.let { fileEntry ->
            storageService.getAbsolutePath(fileEntry)?.let { path ->
                try {
                    // TODO think about buffer!
                    pdfReaderView!!.adapter = PageAdapter(context, MuPDFCore(File(path).readBytes(), path))
                    viewBinding.muPdfWrapper.apply {
                        removeAllViews()
                        addView(pdfReaderView!!)
                    }
                    if (page?.type == PageType.panorama) {
                        pdfReaderView!!.zoomPanoramaPage()
                    }
                } catch (npe: NullPointerException) {
                    Sentry.captureException(npe)
                    finishActivityWithErrorToast()
                }
            } ?: finishActivityWithErrorToast()
        } ?: finishActivityWithErrorToast()
    }

    override fun onDestroyView() {
        viewBinding.muPdfWrapper.removeAllViews()
        pdfReaderView = null
        super.onDestroyView()
    }

    private fun showFramesIfPossible(x: Float, y: Float) {
        log.verbose("Clicked on x: $x, y:$y")
        val pageList = pdfPagerViewModel.pdfPageList.value
        pageList?.let { list ->
            val frameList =
                pdfPagerViewModel.currentItem.value?.let { list[it].frameList } ?: emptyList()
            val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
            frame?.let {
                it.link?.let { link ->
                    if (link.startsWith("art") && link.endsWith(".html")) {
                        lifecycleScope.launch {
                            pdfPagerViewModel.hideDrawerLogo.postValue(false)
                            val article = withContext(Dispatchers.IO) {
                                articleRepository.get(link)
                            }
                            val fragment =
                                if (article?.isImprint() == true) ImprintWebViewFragment()
                                else ArticlePagerFragment()
                            requireActivity().supportFragmentManager.beginTransaction()
                                .add(
                                    R.id.activity_pdf_fragment_placeholder,
                                    fragment,
                                    ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
                                )
                                .addToBackStack(null)
                                .commit()


                            issueContentViewModel.setDisplayable(
                                IssueKey(issueKey),
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
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder().setToolbarColor(color).build()
                )
                .build()
                .apply { launchUrl(requireContext(), Uri.parse(url)) }
        } catch (e: ActivityNotFoundException) {
            val toastHelper = ToastHelper.getInstance(requireContext())
            if (url.startsWith("mailto:")) {
                toastHelper.showToast(R.string.toast_no_email_client)
            } else {
                toastHelper.showToast(R.string.toast_unknown_error)
            }
        }
    }

    private fun finishActivityWithErrorToast() {
        requireActivity().finish()
        toastHelper.showToast(R.string.toast_problem_showing_pdf)
    }
}
