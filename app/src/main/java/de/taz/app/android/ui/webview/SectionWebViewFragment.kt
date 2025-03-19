package de.taz.app.android.ui.webview

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.util.TypedValue.COMPLEX_UNIT_SP
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.UiThread
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.updatePadding
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.whenCreated
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.SectionType
import de.taz.app.android.audioPlayer.AudioPlayerService
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentWebviewSectionBinding
import de.taz.app.android.persistence.repository.BookmarkRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.SnackBarHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.ArticleName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil


class SectionWebViewViewModel(application: Application, savedStateHandle: SavedStateHandle) :
    WebViewViewModel<SectionOperations>(application, savedStateHandle) {

    val sectionFlow = displayableLiveData.asFlow().filterNotNull()
    val issueStubFlow = sectionFlow
        .mapNotNull {
            it.getIssueStub(application.applicationContext)
        }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

}

const val PADDING_RIGHT_OF_LOGO = 20

class SectionWebViewFragment : WebViewFragment<
        SectionOperations,
        SectionWebViewViewModel,
        FragmentWebviewSectionBinding
>() {

    private lateinit var sectionRepository: SectionRepository
    private lateinit var fileEntryRepository: FileEntryRepository
    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var storageService: StorageService
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var audioPlayerService: AudioPlayerService


    override val viewModel by viewModels<SectionWebViewViewModel>()

    private var sectionOperation: SectionOperations? = null
    private lateinit var sectionFileName: String
    private val isFirst: Boolean
        get() = requireArguments().getBoolean(SECTION_IS_FIRST)

    private var bookmarkJob: Job? = null
    private var enqueuedJob: Job? = null

    override val webView: AppWebView
        get() = viewBinding.webView

    override val loadingScreen: View
        get() = viewBinding.loadingScreen.root

    override val appBarLayout: AppBarLayout
        get() = viewBinding.appBarLayout

    override val bottomNavigationLayout: View? = null

    companion object {
        private const val SECTION_FILE_NAME = "SECTION_FILE_NAME"
        private const val SECTION_IS_FIRST = "SECTION_IS_FIRST"

        fun newInstance(section: SectionOperations, isFirst: Boolean): SectionWebViewFragment {
            return SectionWebViewFragment().apply {
                arguments = bundleOf(
                    SECTION_FILE_NAME to section.key,
                    SECTION_IS_FIRST to isFirst
                )
                sectionOperation = section
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        sectionRepository = SectionRepository.getInstance(context.applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(context.applicationContext)
        bookmarkRepository = BookmarkRepository.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        audioPlayerService = AudioPlayerService.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sectionFileName = requireArguments().getString(SECTION_FILE_NAME)!!
        log.debug("Creating a SectionWebViewFragment for $sectionFileName")

        if (sectionOperation != null) {
            viewModel.displayableLiveData.postValue(sectionOperation)
        } else {
            lifecycleScope.launch {
                viewModel.displayableLiveData.postValue(
                    sectionRepository.getStub(sectionFileName)
                )
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            maybeHandlePodcast()
        }
    }

    override fun setHeader(displayable: SectionOperations) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Keep a copy of the current context while running this coroutine.
            // This is necessary to prevent from a crash while calling requireContext() if the
            // Fragment was already being destroyed.
            // If there is no more context available we return from the coroutine immediately.
            val context = this@SectionWebViewFragment.context ?: return@launch

            val issueStub = viewModel.issueStubFlow.first()
            val isWeekend = issueStub.isWeekend && issueStub.validityDate.isNullOrBlank()
            val isWochentaz = issueStub.isWeekend && !issueStub.validityDate.isNullOrBlank()

            if (isWeekend && isFirst) {
                // The first page of the weekend taz should not display the title but the date instead
                viewBinding.apply {
                    headerToolbarContent.updatePadding(
                        top = resources.getDimensionPixelSize(R.dimen.fragment_header_title_weekend_padding_top),
                        bottom = resources.getDimensionPixelSize(R.dimen.fragment_header_title_weekend_padding_top),
                    )
                    section.isVisible = false
                    issueDate.isVisible = false

                    weekendIssueDate.apply {
                        isVisible = true
                        DateHelper.stringToDate(displayable.issueDate)?.let { date ->
                            text = DateHelper.dateToWeekendNotation(date)
                        }
                    }
                }

            } else {
                viewBinding.apply {
                    weekendIssueDate.isVisible = false

                    section.apply {
                        isVisible = true
                        text = displayable.getHeaderTitle()
                    }
                }

                // Change typeface (to Knile) if it is weekend issue but not on title section:
                if (isWeekend || (isWochentaz && !isFirst)) {
                    viewBinding.section.typeface =
                        ResourcesCompat.getFont(context, R.font.appFontKnileSemiBold)
                }

                // On first section "die tageszeitung" or "wochentaz" the header should be bigger:
                if (isFirst && (isWeekend || isWochentaz)) {
                    val textPixelSize =
                        resources.getDimensionPixelSize(R.dimen.fragment_header_title_section_text_size)
                    val textSpSize =
                        resources.getDimension(R.dimen.fragment_header_title_section_text_size)
                    viewBinding.section.apply {
                        setTextSize(COMPLEX_UNIT_SP, textSpSize)
                        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                            this,
                            TextViewCompat.getAutoSizeMinTextSize(this),
                            textPixelSize,
                            ceil(0.1 * resources.displayMetrics.density).toInt(),
                            TypedValue.COMPLEX_UNIT_PX
                        )
                        translationY =
                            resources.getDimension(R.dimen.fragment_header_section_title_y_translation)
                    }
                }

                DateHelper.stringToDate(displayable.issueDate)?.let { date ->
                    viewBinding.issueDate.apply {
                        isVisible = true
                        text = when {
                            isWeekend ->
                                // Regular Weekend Issue
                                DateHelper.dateToWeekendNotation(date)

                            isWochentaz ->
                                // Wochentaz Issue
                                DateHelper.dateToWeekNotation(
                                    date, requireNotNull(issueStub.validityDate)
                                )

                            else -> DateHelper.dateToLowerCaseString(date)
                        }
                    }
                }
            }

            activity?.findViewById<ImageView>(R.id.drawer_logo)?.let {
                resizeHeaderSectionTitle(it.width)
            }
            applyExtraPaddingOnCutoutDisplay()
        }

    }

    override fun onResume() {
        activity?.findViewById<ImageView>(R.id.drawer_logo)?.let {
            resizeHeaderSectionTitle(it.width)
            it.addOnLayoutChangeListener(resizeDrawerLogoListener)
        }
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            val issueStub = viewModel.issueStubFlow.first()
            val section = viewModel.sectionFlow.first()
            tracker.trackSectionScreen(issueStub.issueKey, section)
        }
    }

    override fun onPause() {
        activity?.findViewById<ImageView>(R.id.drawer_logo)?.removeOnLayoutChangeListener(
            resizeDrawerLogoListener
        )
        super.onPause()
    }

    override fun onDestroyView() {
        bookmarkJob?.cancel()
        enqueuedJob?.cancel()
        super.onDestroyView()
    }

    override fun onPageRendered() {
        super.onPageRendered()
        viewLifecycleOwner.lifecycleScope.launch {
            restoreLastScrollPosition()
            hideLoadingScreen()
        }
    }

    override fun reloadAfterCssChange() {
        viewLifecycleOwner.lifecycleScope.launch {
            whenCreated {
                if (!isRendered) {
                    return@whenCreated
                }

                webView.injectCss()
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N)
                    webView.reload()
            }
        }
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

    /**
     * Adjust padding when we have cutout display
     */
    private fun applyExtraPaddingOnCutoutDisplay() {
        viewLifecycleOwner.lifecycleScope.launch {
            val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
            if (extraPadding > 0 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                viewBinding.collapsingToolbarLayout.setPadding(0, extraPadding, 0, 0)
            }
        }
    }

    @UiThread
    private fun runIfWebViewReady(function: () -> Unit) {
        if (!isRendered) {
            return
        }

        try {
            function()
        } catch (npe: NullPointerException) {
            // It is possible that given function() is called from a coroutine when the
            // fragments view is already destroyed or not ready yet. In this case the `webView`
            // property will be `null`. Unfortunately it is defined as non-null in kotlin,
            // thus we catch and ignore this exception.
        }
    }

    // region bookmark handling
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
        val articleFileNames = articleNamesInWebView.mapNotNull {
            issueViewerViewModel.findArticleStubByArticleName(it)?.articleFileName
        }

        setupBookmarkStateFlows(articleFileNames)

        return bookmarkRepository.filterIsBookmarked(articleFileNames).map {
            ArticleName.fromArticleFileName(it)
        }
    }

    override suspend fun onSetBookmark(
        articleName: String,
        isBookmarked: Boolean,
        showNotification: Boolean,
    ) {
        val articleStub = issueViewerViewModel.findArticleStubByArticleName(articleName)
        if (articleStub != null) {
            if (isBookmarked) {
                bookmarkRepository.addBookmarkAsync(articleStub).await()
                SnackBarHelper.showBookmarkSnack(
                    context = requireContext(),
                    view = viewBinding.root,
                    anchor = bottomNavigationLayout,
                )
            } else {
                bookmarkRepository.removeBookmarkAsync(articleStub).await()
                SnackBarHelper.showDebookmarkSnack(
                    context = requireContext(),
                    view = viewBinding.root,
                    anchor = bottomNavigationLayout,
                )
            }
        } else {
            log.warn("Could not set bookmark for articleName=$articleName as no articleFileName was found.")
        }
    }

    @UiThread
    private fun setWebViewBookmarkState(articleFileName: String, isBookmarked: Boolean) {
        val articleName = ArticleName.fromArticleFileName(articleFileName)
        runIfWebViewReady {
            webView.callTazApi("onBookmarkChange", articleName, isBookmarked)
        }
    }
    // endregion

    // region playlist handling
    private fun setupEnqueuedStateFlows(articleFileNames: List<String>) {
        // Create a new coroutine scope to listen to playlist enqueue changes
        // When the enqueuedJob is canceled all coroutines launched from associated scope will be canceled, too
        val newEnqueuedJob = Job()
        enqueuedJob?.cancel()
        enqueuedJob = newEnqueuedJob
        val enqueuedScope = CoroutineScope(Dispatchers.Default + newEnqueuedJob)

        enqueuedJob?.isActive

        // Create a coroutine for each article listening for its playlist enqueue changes
        articleFileNames.forEach { articleFileName ->
            enqueuedScope.launch {
                audioPlayerService.isInPlaylistFlow(articleFileName).collect {
                    withContext(Dispatchers.Main) {
                        setWebViewEnqueuedState(articleFileName, it)
                    }
                }
            }
        }
    }

    override suspend fun setupEnqueuedHandling(articleNamesInWebView: List<String>): List<String> {
        val articleFileNames = articleNamesInWebView.mapNotNull {
            issueViewerViewModel.findArticleStubByArticleName(it)?.articleFileName
        }

        setupEnqueuedStateFlows(articleFileNames)

        val enqueuedArticlesInThisWebView = audioPlayerService.persistedPlaylistState.value.items.filter {
            it.playableKey in articleFileNames
        }.mapNotNull {
            it.playableKey
        }

        return enqueuedArticlesInThisWebView
    }

    override suspend fun onEnqueued(
        articleName: String,
        setEnqueued: Boolean,
    ) {
        val articleStub = issueViewerViewModel.findArticleStubByArticleName(articleName)
        if (articleStub != null && articleStub.hasAudio) {
            if (setEnqueued) {
                try {
                    audioPlayerService.enqueueArticle(articleStub.key)
                } catch (e: Exception) {
                    log.error("Could not play article audio (${articleStub.key})", e)
                }
            } else {
                try {
                    audioPlayerService.removeItemFromPlaylist(articleStub.key)
                } catch (e: Exception) {
                    log.error("Could not remove item from playlist (${articleStub.key})", e)
                }
            }
        } else {
            log.warn("Could not set enqueued for $articleName as articleStub is null (or article has no audio).")
        }
    }

    @UiThread
    private fun setWebViewEnqueuedState(articleFileName: String, isEnqueued: Boolean) {
        val articleName = ArticleName.fromArticleFileName(articleFileName)
        runIfWebViewReady {
            webView.callTazApi("onEnqueuedChange", articleName, isEnqueued)
        }
    }
    // endregion

    @SuppressLint("ClickableViewAccessibility")
    private suspend fun maybeHandlePodcast() {
        val issueStub = viewModel.issueStubFlow.first()
        val section = viewModel.sectionFlow.first()
        val podcast = section.getPodcast(requireContext().applicationContext)
        if (section.type == SectionType.podcast && podcast != null) {
            val onGestureListener = object : SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    audioPlayerService.playPodcast(issueStub, section, podcast)
                    return true
                }
            }
            val gestureDetectorCompat = GestureDetector(requireContext(), onGestureListener).apply {
                setIsLongpressEnabled(false)
            }
            webView.addOnTouchListener { _, event ->
                gestureDetectorCompat.onTouchEvent(event)
            }

        } else {
            webView.clearOnTouchListener()
        }
    }
}