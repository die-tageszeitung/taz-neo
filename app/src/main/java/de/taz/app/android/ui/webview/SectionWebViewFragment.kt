package de.taz.app.android.ui.webview

import android.app.Application
import android.content.Context
import android.graphics.Point
import android.os.Bundle
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.UiThread
import androidx.core.os.bundleOf
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.CollapsingToolbarLayout
import de.taz.app.android.KNILE_SEMIBOLD_RESOURCE_FILE_NAME
import de.taz.app.android.R
import de.taz.app.android.api.models.Section
import de.taz.app.android.databinding.FragmentWebviewSectionBinding
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.ArticleName
import kotlinx.coroutines.*
import kotlin.math.ceil

class SectionWebViewViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    WebViewViewModel<Section>(application, savedStateHandle)

const val PADDING_RIGHT_OF_LOGO = 20

class SectionWebViewFragment : WebViewFragment<
        Section,
        SectionWebViewViewModel,
        FragmentWebviewSectionBinding
>() {

    private lateinit var sectionRepository: SectionRepository
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var storageService: StorageService
    private lateinit var toastHelper: ToastHelper

    override val viewModel by viewModels<SectionWebViewViewModel>()

    override val nestedScrollViewId: Int = R.id.web_view_wrapper

    private lateinit var sectionFileName: String
    private val isFirst: Boolean
        get() = requireArguments().getBoolean(SECTION_IS_FIRST)

    companion object {
        private const val SECTION_FILE_NAME = "SECTION_FILE_NAME"
        private const val SECTION_IS_FIRST = "SECTION_IS_FIRST"

        fun newInstance(sectionFileName: String, isFirst: Boolean): SectionWebViewFragment {
            return SectionWebViewFragment().apply {
                arguments = bundleOf(
                    SECTION_FILE_NAME to sectionFileName,
                    SECTION_IS_FIRST to isFirst
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sectionFileName = requireArguments().getString(SECTION_FILE_NAME)!!
        log.debug("Creating a SectionWebViewFragment for $sectionFileName")

        lifecycleScope.launch {
            viewModel.displayableLiveData.postValue(
                sectionRepository.get(sectionFileName)
            )
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        sectionRepository = SectionRepository.getInstance(requireContext().applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(requireContext().applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(requireContext().applicationContext)
        storageService = StorageService.getInstance(requireContext().applicationContext)
        toastHelper = ToastHelper.getInstance(requireContext().applicationContext)
    }

    override fun setHeader(displayable: Section) {
        activity?.apply {

            lifecycleScope.launch(Dispatchers.Main) {
                val issueStub = displayable.getIssueStub(requireContext().applicationContext)
                val isWeekend = issueStub?.isWeekend == true && issueStub.validityDate.isNullOrBlank()
                val isWochentaz =  issueStub?.isWeekend == true && !issueStub.validityDate.isNullOrBlank()

                val toolbar =
                    view?.findViewById<CollapsingToolbarLayout>(R.id.collapsing_toolbar_layout)
                toolbar?.removeAllViews()

                // The first page of the weekend taz should not display the title but the date instead
                val layout =
                    if (isWeekend && isFirst) {
                        R.layout.fragment_webview_header_title_weekend_section
                    } else {
                        R.layout.fragment_webview_header_section
                    }

                val headerView =
                    LayoutInflater.from(requireContext()).inflate(layout, toolbar, true)
                val sectionTextView = headerView.findViewById<TextView>(R.id.section)

                // Change typeface (to Knile) if it is weekend issue but not on title section:
                if (isWeekend || (isWochentaz && !isFirst)) {
                    val weekendTypeface = withContext(Dispatchers.IO) {
                        val weekendTypefaceFileEntry =
                            fileEntryRepository.get(KNILE_SEMIBOLD_RESOURCE_FILE_NAME)
                        val weekendTypefaceFile =
                            weekendTypefaceFileEntry?.let(storageService::getFile)
                        weekendTypefaceFile?.let {
                            FontHelper.getInstance(requireContext().applicationContext)
                                .getTypeFace(it)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        sectionTextView?.typeface =
                            weekendTypeface
                    }
                }


                sectionTextView?.text = displayable.getHeaderTitle()
                DateHelper.stringToDate(displayable.issueDate)?.let { date ->
                    headerView.findViewById<TextView>(R.id.issue_date)?.apply {
                        text = when {
                            isWeekend ->
                                // Regular Weekend Issue
                                DateHelper.dateToWeekendNotation(date)
                            isWochentaz ->
                                // Wochentaz Issue
                                DateHelper.dateToWeekNotation(date, requireNotNull(issueStub?.validityDate))
                            else ->
                                DateHelper.dateToLowerCaseString(date)
                        }
                    }
                }

                // On first section "die tageszeitung" or "wochentaz" the header should be bigger:
                if (isFirst && (isWeekend || isWochentaz)) {
                    val textPixelSize =
                        resources.getDimensionPixelSize(R.dimen.fragment_header_title_section_text_size)
                    val textSpSize =
                        resources.getDimension(R.dimen.fragment_header_title_section_text_size)
                    sectionTextView?.apply {
                        setTextSize(COMPLEX_UNIT_SP, textSpSize)
                        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            this,
                            TextViewCompat.getAutoSizeMinTextSize(this),
                            textPixelSize,
                            ceil(0.1 * resources.displayMetrics.density).toInt(),
                            TypedValue.COMPLEX_UNIT_PX
                        )
                        translationY = resources.getDimension(R.dimen.fragment_header_section_title_y_translation)
                    }
                }

                activity?.findViewById<ImageView>(R.id.drawer_logo)?.let {
                    resizeHeaderSectionTitle(it.width)
                }
            }
        }
    }

    override fun onResume() {
        activity?.findViewById<ImageView>(R.id.drawer_logo)?.let {
            resizeHeaderSectionTitle(it.width)
            it.addOnLayoutChangeListener(resizeDrawerLogoListener)
        }
        super.onResume()
    }

    override fun onPause() {
        activity?.findViewById<ImageView>(R.id.drawer_logo)?.removeOnLayoutChangeListener(
            resizeDrawerLogoListener
        )
        super.onPause()
    }

    private val resizeDrawerLogoListener =
        View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            resizeHeaderSectionTitle(v.width)
        }

    /**
     * ensure the text is not shown below the drawerLogo
     * @param drawerLogoWidth: Int - the width of the current logo shown in the drawer
     */
    private fun resizeHeaderSectionTitle(drawerLogoWidth: Int) {
        setMaxSizeDependingOnDrawerLogo(R.id.section, drawerLogoWidth)
        setMaxSizeDependingOnDrawerLogo(R.id.issue_date, drawerLogoWidth)
    }

    private fun setMaxSizeDependingOnDrawerLogo(@IdRes viewId: Int, drawerLogoWidth: Int) {
        val point = Point()
        activity?.windowManager?.defaultDisplay?.getSize(point)
        view?.findViewById<TextView>(viewId)?.apply {
            val parentView = (parent as View)
            val paddingInPixel = (PADDING_RIGHT_OF_LOGO / resources.displayMetrics.density).toInt()
            width =
                point.x - drawerLogoWidth - parentView.marginRight - marginLeft - marginRight - paddingInPixel
        }
    }


    override fun onDestroyView() {
        bookmarkJob?.cancel()
        super.onDestroyView()
    }

    private var bookmarkJob: Job? = null

    private fun setupBookmarkStateFlows(articleFileNames: List<String>) {
        // Create a new coroutine scope to listen to bookmark changes
        // When the bookmarkJob is canceled all coroutines launched from associated scope will be canceled, too
        val newBookmarkJob = Job()
        bookmarkJob?.cancel()
        bookmarkJob = newBookmarkJob
        val bookmarkScope = CoroutineScope(Dispatchers.Default + newBookmarkJob)

        bookmarkJob?.isActive

        // Create a coroutine for each article listening for its bookmark changes
        articleFileNames.forEach { articleFileName ->
            bookmarkScope.launch {
                bookmarkRepository.createBookmarkStateFlow(articleFileName).collect {
                    withContext(Dispatchers.Main) {
                        setWebViewBookmarkState(articleFileName, it)
                    }
                }
            }
        }
    }


    override suspend fun setupBookmarkHandling(articleNamesInWebView: List<String>): List<String> {
        val articleFileNames = articleNamesInWebView.mapNotNull { issueViewerViewModel.findArticleFileName(it) }

        setupBookmarkStateFlows(articleFileNames)

        return bookmarkRepository.filterIsBookmarked(articleFileNames).map {
            ArticleName.fromArticleFileName(it)
        }
    }

    override suspend fun onSetBookmark(
        articleName: String,
        isBookmarked: Boolean,
        showNotification: Boolean
    ) {
        val articleFileName = issueViewerViewModel.findArticleFileName(articleName)
        if (articleFileName != null) {
            if (isBookmarked) {
                bookmarkRepository.addBookmarkAsync(articleFileName).await()
                toastHelper.showToast(R.string.toast_article_bookmarked)
            } else {
                bookmarkRepository.removeBookmarkAsync(articleFileName).await()
                toastHelper.showToast(R.string.toast_article_debookmarked)
            }
        } else {
            log.warn("Could not set bookmark for articleName=$articleName as no articleFileName was found.")
        }
    }

    @UiThread
    private fun setWebViewBookmarkState(articleFileName: String, isBookmarked: Boolean) {
        val articleName = ArticleName.fromArticleFileName(articleFileName)
        try {
            webView.callTazApi("onBookmarkChange", articleName, isBookmarked)
        } catch (npe: NullPointerException) {
            // It is possible that setWebViewBookmarkState() is called from a coroutine when the
            // fragments view is already destroyed or not ready yet. In this case the `webView`
            // property will be `null`. Unfortunately it is defined as non-null in kotlin,
            // thus we catch and ignore this exception.
        }
    }
}