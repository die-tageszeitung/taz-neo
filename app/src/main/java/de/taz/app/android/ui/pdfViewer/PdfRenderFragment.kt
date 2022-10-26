package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.artifex.mupdf.fitz.FileStream
import com.artifex.mupdf.viewer.MuPDFCore
import com.artifex.mupdf.viewer.PageAdapter
import com.artifex.mupdf.viewer.PageView
import com.artifex.mupdfdemo.ReaderView
import de.taz.app.android.R
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageType
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentPdfRenderBinding
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.NotFoundException
import de.taz.app.android.persistence.repository.PageRepository
import de.taz.app.android.singletons.KeepScreenOnHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import kotlinx.coroutines.launch
import java.io.File


class PdfRenderFragment : BaseMainFragment<FragmentPdfRenderBinding>() {

    private var page: Page? = null

    companion object {
        private const val PAGE_NAME = "PAGE_NAME"
        private const val SCALE_FACTOR_FOR_PANORAMA_PAGES = 2f

        fun newInstance(page: Page): PdfRenderFragment {
            val fragment = PdfRenderFragment()
            fragment.page = page
            fragment.arguments = Bundle().also { it.putString(PAGE_NAME, page.pagePdf.name) }
            return fragment
        }
    }

    private val log by Log
    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()

    private var pdfReaderView: MuPDFReaderView? = null

    private lateinit var articleRepository: ArticleRepository
    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private lateinit var storageService: StorageService
    private lateinit var pageRepository: PageRepository
    private lateinit var toastHelper: ToastHelper

    // Set to true if we have to force a pdf view update because we did set an initial manual scale
    private var requestPdfUpdateOnResume = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val applicationContext = context.applicationContext
        articleRepository = ArticleRepository.getInstance(applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(applicationContext)
        storageService = StorageService.getInstance(applicationContext)
        pageRepository = PageRepository.getInstance(applicationContext)
        toastHelper = ToastHelper.getInstance(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // FIXME (johannes): kept for timing debugging
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                tazApiCssDataStore.keepScreenOn.asFlow().collect {
                    KeepScreenOnHelper.toggleScreenOn(it, activity)
                }
            }
        }

        pdfReaderView = MuPDFReaderView(requireContext()).apply {
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

        val pageName = arguments?.getString(PAGE_NAME)

        // if page is null get Page from DB and PAGE_NAME argument
        if (page != null) {
            initializeThePageAdapter(requireNotNull(page))
        } else if (pageName != null) {
            initPage(pageName)
        } else {
            log.error("Missing page or PAGE_NAME arguments")
        }
    }

    override fun onResume() {
        super.onResume()
        maybeUpdatePdfView()
    }

    override fun onDestroyView() {
        viewBinding.muPdfWrapper.removeAllViews()
        pdfReaderView = null
        super.onDestroyView()
    }

    private fun initPage(pageName: String) {
        lifecycleScope.launch {
            try {
                page = pageRepository.getOrThrow(pageName)
                initializeThePageAdapter(requireNotNull(page))
            } catch (nfe: NotFoundException) {
                log.error("Could not find page for pageName $pageName")
            }
        }
    }

    private fun initializeThePageAdapter(page: Page) {
        val pdfReaderView = this.pdfReaderView
        val path = storageService.getAbsolutePath(page.pagePdf)

        if (pdfReaderView != null && path != null) {
            val muPdfInputStream = FileStream(File(path), "r")
            pdfReaderView.adapter = PageAdapter(context, MuPDFCore(muPdfInputStream, path))
            viewBinding.muPdfWrapper.apply {
                removeAllViews()
                addView(pdfReaderView)
            }
            if (page.type == PageType.panorama) {
                zoomPanoramaPageInPortrait()
            }

        } else {
            log.error("Failed initializing the pdf page adapter (PdfReaderView is null? ${pdfReaderView == null}, path is null? ${path == null}")
            finishActivityWithErrorToast()
        }
    }


    private fun showFramesIfPossible(x: Float, y: Float) {
        log.verbose("Clicked on x: $x, y:$y")
        val frameList = page?.frameList ?: emptyList()
        val frame = frameList.firstOrNull { it.x1 <= x && x < it.x2 && it.y1 <= y && y < it.y2 }
        if (frame != null) {
            frame.link?.let {
                pdfPagerViewModel.onFrameLinkClicked(it)
            }
        } else {
            frameList.forEach {
                log.debug("possible frame: $it")
            }
        }
    }

    private fun finishActivityWithErrorToast() {
        requireActivity().finish()
        toastHelper.showToast(R.string.toast_problem_showing_pdf)
    }

    private fun zoomPanoramaPageInPortrait() {
        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            pdfReaderView?.mScale = SCALE_FACTOR_FOR_PANORAMA_PAGES
            requestPdfUpdateOnResume = true
        }
    }

    /**
     * Updates the [PageView] (which is behind the [ReaderView]),
     * so the page is rendered in high quality.
     * This is done automatically when changing the layout with gestures (scrolling, zooming, etc),
     * but not when done programmatically, eg. on zooming in the panorama pages in adapter.
     */
    private fun maybeUpdatePdfView() {
        if (requestPdfUpdateOnResume) {
            pdfReaderView?.apply {
                (displayedView as? PageView)
                    ?.updateHq(false)
                    ?: log.warn("cannot cast as PageView as displayedView is null")
            }

            requestPdfUpdateOnResume = false
        }
    }
}
