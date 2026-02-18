package de.taz.app.android.ui.webview.pager

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.webkit.WebView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_DRAGGING
import androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE
import com.google.android.material.behavior.HideViewOnScrollBehavior
import com.google.android.material.behavior.HideViewOnScrollBehavior.EDGE_BOTTOM
import com.google.android.material.behavior.HideViewOnScrollBehavior.EDGE_LEFT
import com.google.android.material.behavior.HideViewOnScrollBehavior.STATE_SCROLLED_IN
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.api.models.SectionType
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.coachMarks.CoachMarkDialog
import de.taz.app.android.coachMarks.SectionBookmarkCoachMark
import de.taz.app.android.coachMarks.SectionPlaylistCoachMark
import de.taz.app.android.coachMarks.TazLogoCoachMark
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentWebviewSectionPagerBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.TazViewerFragment
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.drawer.LogoState
import de.taz.app.android.ui.issueViewer.IssueContentDisplayMode
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.ui.webview.HelpFabViewModel
import de.taz.app.android.ui.webview.SectionImprintWebViewFragment
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.getHideViewOnScrollBehavior
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch


class SectionPagerFragment : BaseMainFragment<FragmentWebviewSectionPagerBinding>() {
    private val log by Log

    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()
    private val helpFabViewModel: HelpFabViewModel by activityViewModels()

    private lateinit var generalDataStore: GeneralDataStore

    private lateinit var tracker: Tracker
    private var showFab = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = Tracker.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding?.apply {
            feedLogo.getHideViewOnScrollBehavior()?.setViewEdge(EDGE_LEFT)
            feedLogo.getHideViewOnScrollBehavior()
                ?.addOnScrollStateChangedListener { _, scrollState ->
                    if (scrollState == STATE_SCROLLED_IN) {
                        drawerAndLogoViewModel.setFeedLogo()
                    } else {
                        drawerAndLogoViewModel.setBurgerIcon()
                    }
                }
            feedLogo.setOnClickListener {
                tracker.trackDrawerOpenEvent(dragged = false)
                drawerAndLogoViewModel.openDrawer()
            }
            burgerLogo.setOnClickListener {
                tracker.trackDrawerOpenEvent(dragged = false)
                drawerAndLogoViewModel.openDrawer()
            }

            webviewPagerViewpager.reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)

            initializeDrawerLogos()
            setupViewPager()
            setupFAB()

            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        combine(
                            issueContentViewModel.sectionListFlow,
                            issueContentViewModel.displayableKeyFlow
                        ) { sectionStubs, displayableKey ->
                            if (
                                sectionStubs.map { it.key } !=
                                (webviewPagerViewpager.adapter as? SectionPagerAdapter)?.sectionStubs?.map { it.key }
                            ) {
                                log.debug("New set of sections: ${sectionStubs.map { it.key }}")
                                webviewPagerViewpager.adapter =
                                    SectionPagerAdapter(sectionStubs)
                                loadingScreen.root.visibility = View.GONE
                            }
                            tryScrollToSection(displayableKey)
                        }.collect {}
                    }

                    launch {
                        helpFabViewModel.showHelpFabFlow.collect {
                            toggleHelpFab(it)
                        }
                    }

                    launch {
                        issueContentViewModel.goPrevious.collect {
                            webviewPagerViewpager.currentItem -= 1
                        }
                    }

                    launch {
                        issueContentViewModel.goNext.collect {
                            webviewPagerViewpager.currentItem += 1
                        }
                    }

                    launch {
                        // in an ideal world this would be handled in DrawerViewController, but we
                        // would need to iterate all of the views, that wouldn't be performant
                        drawerAndLogoViewModel.drawerState.collect {
                            if (it.logoState == LogoState.FEED) {
                                feedLogo.getHideViewOnScrollBehavior()?.slideIn(feedLogo)
                                // wait for the logo to be slided in far enough to hide the burger
                                delay(226) // = HideViewOnScrollBehavior.DEFAULT_ENTER_ANIMATION_DURATION_MS
                                burgerLogo.visibility = View.GONE
                            } else {
                                burgerLogo.visibility = View.VISIBLE
                                feedLogo.getHideViewOnScrollBehavior()?.slideOut(feedLogo)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun initializeDrawerLogos() = viewBinding?.apply {
        lifecycleScope.launch {
            val dvc = (parentFragment?.parentFragment as? TazViewerFragment)?.drawerViewController
            dvc?.ensureFeedLogo(feedLogo)
            dvc?.ensureBurgerIcon(burgerWrapper, burgerLogo)

            // Adjust padding when we have cutout display
            val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
            if (extraPadding > 0 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                viewBinding?.feedLogo?.translationY += extraPadding
                viewBinding?.burgerWrapper?.translationY += extraPadding
            }
        }
    }

    private fun setupViewPager() {
        viewBinding?.webviewPagerViewpager?.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
        }
    }

    /**
     * On edge to edge we need to properly update the margins of the FAB:
     */
    private fun setupFAB() {
        viewBinding?.sectionPagerFabHelp?.let { floatingActionButton ->

            ViewCompat.setOnApplyWindowInsetsListener(floatingActionButton) { v, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                // Apply the insets as a margin to the view. This solution sets
                // only the bottom, left, and right dimensions, but you can apply whichever
                // insets are appropriate to your layout. You can also update the view padding
                // if that's more appropriate.
                val marginBottomFromDimens =
                    resources.getDimensionPixelSize(R.dimen.fab_margin_bottom)
                v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    bottomMargin = insets.bottom + marginBottomFromDimens
                }

                // Return CONSUMED if you don't want the window insets to keep passing
                // down to descendant views.
                WindowInsetsCompat.CONSUMED
            }
            floatingActionButton.setOnClickListener {
                log.verbose("show coach marks in section pager")
                showCoachMarks()
            }

            floatingActionButton.getHideViewOnScrollBehavior()?.setViewEdge(EDGE_BOTTOM)

            issueContentViewModel.fabHelpEnabledFlow
                .flowWithLifecycle(lifecycle)
                .onEach {
                    floatingActionButton.isVisible = it
                    showFab = it
                }.launchIn(lifecycleScope)
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        private var lastPage: Int? = null
        override fun onPageSelected(position: Int) {
            val sectionStubs =
                (viewBinding?.webviewPagerViewpager?.adapter as? SectionPagerAdapter)?.sectionStubs
                    ?: emptyList()
            // if we are beyond last position we are the imprint
            val isImprint = position == sectionStubs.size
            if (lastPage != null && lastPage != position && !isImprint) {
                runIfNotNull(
                    issueContentViewModel.issueKeyAndDisplayableKeyFlow.value?.issueKey,
                    sectionStubs[position]
                ) { issueKey, displayable ->
                    if (issueContentViewModel.activeDisplayModeFlow.value == IssueContentDisplayMode.Section) {
                        issueContentViewModel.setDisplayable(
                            IssueKeyWithDisplayableKey(
                                issueKey,
                                displayable.key
                            )
                        )
                    }
                }
            }
            // Always show the logo on section page change (except for advertisement or podcast):
            hideOrShowLogoIfNecessary(lastPage, position)
            lastPage = position
        }

        // To detect whether we have swiped through the section pager manually, it is not enough to
        // listen on the `onPageSelected` as that is triggered too by clicking a section in the drawer.
        // So we need to really listen to `onPageSCrolled` to detect real horizontal swipes:
        var scrolled = false
        override fun onPageScrollStateChanged(state: Int) {
            super.onPageScrollStateChanged(state)
            if (state == SCROLL_STATE_DRAGGING) {
                scrolled = true
            }
            if (state == SCROLL_STATE_IDLE && scrolled) {
                scrolled = false
            }
        }
    }

    override fun onDestroyView() {
        viewBinding?.webviewPagerViewpager?.adapter = null
        super.onDestroyView()
    }

    override fun onStart() {
        viewBinding?.webviewPagerViewpager?.registerOnPageChangeCallback(pageChangeListener)
        super.onStart()
    }

    override fun onStop() {
        viewBinding?.webviewPagerViewpager?.unregisterOnPageChangeCallback(pageChangeListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        viewBinding?.navigationBottomWebviewPager?.let {
            requireActivity().setupBottomNavigation(
                it,
                BottomNavigationItem.ChildOf(BottomNavigationItem.Home)
            )
        }
    }

    private inner class SectionPagerAdapter(val sectionStubs: List<SectionStub>) :
        FragmentStateAdapter(this@SectionPagerFragment) {

        override fun createFragment(position: Int): Fragment {
            val isImprint = position == sectionStubs.size
            val fragment = if (isImprint) {
                SectionImprintWebViewFragment()
            } else {
                val sectionStub = sectionStubs[position]
                val isFirstSection = position == 0
                SectionWebViewFragment.newInstance(sectionStub, isFirstSection)
            }
            return fragment
        }

        // The imprint is added in addition to the sectionStubs - thus we have to add + 1
        override fun getItemCount(): Int = sectionStubs.size + 1
    }

    private suspend fun tryScrollToSection(displayableKey: String) {
        if (displayableKey.startsWith("sec")) {
            log.debug("Section selected: $displayableKey")
            issueContentViewModel.lastSectionKey = displayableKey

            getSupposedPagerPosition()?.let {
                try {
                    if (it >= 0 && it != getCurrentPagerPosition()) {
                        viewBinding?.webviewPagerViewpager?.setCurrentItem(it, false)
                    }
                } catch (e: IndexOutOfBoundsException) {
                    val message = "Tried to access position outside of adapter. ${e.message}"
                    log.warn(message)
                    SentryWrapper.captureException(e)
                }
            }
        }
    }

    private fun getCurrentPagerPosition(): Int {
        return viewBinding?.webviewPagerViewpager?.currentItem ?: 0
    }

    private suspend fun getSupposedPagerPosition(): Int? {
        val adapter = (viewBinding?.webviewPagerViewpager?.adapter as? SectionPagerAdapter)
        val sectionStubs = adapter?.sectionStubs
        val position = sectionStubs?.indexOfFirst {
            it.key == issueContentViewModel.displayableKeyFlow.first()
        }

        return if (position != null && position >= 0) {
            position
        } else {
            null
        }
    }

    /**
     * On advertisements we hide the drawer logo.
     */
    private fun hideOrShowLogoIfNecessary(lastPage: Int?, position: Int) {
        val sectionsStubs =
            (viewBinding?.webviewPagerViewpager?.adapter as? SectionPagerAdapter)?.sectionStubs
                ?: return
        if (sectionsStubs.isEmpty()) return
        val currentSection = try {
            sectionsStubs[position]
        } catch (ioob: IndexOutOfBoundsException) {
            log.error("could not get section of position $position. ${ioob.message}")
            return
        }
        val isAdvertisement = currentSection.type == SectionType.advertisement
        val isPodcast = currentSection.type == SectionType.podcast
        if (isAdvertisement || isPodcast) {
            drawerAndLogoViewModel.hideLogo()
            viewBinding?.sectionPagerFabHelp?.hide()
        } else {
            if (showFab) {
                viewBinding?.sectionPagerFabHelp?.show()
            }
        }
    }

    private fun showCoachMarks() {
        val tazLogoCoachMark =
            TazLogoCoachMark.create(requireActivity().findViewById(R.id.drawer_logo))

        val sectionBookmarkCoachMark = SectionBookmarkCoachMark()
        val sectionPlaylistCoachMark = SectionPlaylistCoachMark()
        val coachMarks = listOf(
            tazLogoCoachMark,
            sectionBookmarkCoachMark,
            sectionPlaylistCoachMark,
        )
        CoachMarkDialog.create(coachMarks).show(childFragmentManager, CoachMarkDialog.TAG)
    }

    private suspend fun toggleHelpFab(show: Boolean) {
        if (issueContentViewModel.fabHelpEnabledFlow.first()) {
            val fab = viewBinding?.sectionPagerFabHelp
            val layoutParams = fab?.layoutParams
            if (layoutParams is CoordinatorLayout.LayoutParams) {
                val behavior = layoutParams.behavior
                if (behavior is HideViewOnScrollBehavior) {
                    if (show) {
                        behavior.slideIn(fab)
                    } else {
                        behavior.slideOut(fab)
                    }
                }
            }
        }
    }
}
