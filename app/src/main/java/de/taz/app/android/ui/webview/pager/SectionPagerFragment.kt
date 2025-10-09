package de.taz.app.android.ui.webview.pager

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
import de.taz.app.android.R
import de.taz.app.android.WEBVIEW_DRAG_SENSITIVITY_FACTOR
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.api.models.SectionType
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.coachMarks.CoachMarkDialog
import de.taz.app.android.coachMarks.SectionBookmarkCoachMark
import de.taz.app.android.coachMarks.SectionPlaylistCoachMark
import de.taz.app.android.coachMarks.TazLogoCoachMark
import de.taz.app.android.databinding.FragmentWebviewSectionPagerBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.issueViewer.IssueContentDisplayMode
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.ui.webview.SectionImprintWebViewFragment
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch


class SectionPagerFragment : BaseMainFragment<FragmentWebviewSectionPagerBinding>() {
    private val log by Log

    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()

    private lateinit var tracker: Tracker
    private var showFab = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.webviewPagerViewpager.apply {
            reduceDragSensitivity(WEBVIEW_DRAG_SENSITIVITY_FACTOR)
        }
        setupViewPager()
        setupDrawerLogoGhost()
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
                            (viewBinding.webviewPagerViewpager.adapter as? SectionPagerAdapter)?.sectionStubs?.map { it.key }
                        ) {
                            log.debug("New set of sections: ${sectionStubs.map { it.key }}")
                            viewBinding.webviewPagerViewpager.adapter =
                                SectionPagerAdapter(sectionStubs)
                            viewBinding.loadingScreen.root.visibility = View.GONE
                        }
                        tryScrollToSection(displayableKey)
                    }.collect {}
                }

                launch {
                    issueContentViewModel.activeDisplayModeFlow.collect {
                        // We show here the couch mark for horizontal swipe. It is at this place because
                        // the [SectionPagerFragment] is always created in the [IssueViewerActivity], even if
                        // an article is shown. But the [activeDisplayMode] gives us the indication that we
                        // are on an section.
                        if (it == IssueContentDisplayMode.Section) {
                     //       HorizontalSectionSwipeCoachMark(this@SectionPagerFragment)
                       //         .maybeShow()
                        }
                    }
                }
            }
        }
    }

    private fun setupViewPager() {
        viewBinding.webviewPagerViewpager.apply {
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 2
        }
    }

    /**
     * On edge to edge we need to properly update the margins of the FAB:
     */
    private fun setupFAB() {
        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.sectionPagerFabHelp) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the insets as a margin to the view. This solution sets
            // only the bottom, left, and right dimensions, but you can apply whichever
            // insets are appropriate to your layout. You can also update the view padding
            // if that's more appropriate.
            val marginBottomFromDimens = resources.getDimensionPixelSize(R.dimen.fab_margin_bottom)
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom + marginBottomFromDimens
            }

            // Return CONSUMED if you don't want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }
        viewBinding.sectionPagerFabHelp.setOnClickListener {
            log.verbose("show coach marks in section pager")
            showCoachMarks()
        }

        issueContentViewModel.fabHelpEnabledFlow
            .flowWithLifecycle(lifecycle)
            .onEach {
                viewBinding.sectionPagerFabHelp.isVisible = it
                showFab = it
            }.launchIn(lifecycleScope)
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        private var lastPage: Int? = null
        override fun onPageSelected(position: Int) {
            val sectionStubs =
                (viewBinding.webviewPagerViewpager.adapter as SectionPagerAdapter).sectionStubs
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
        viewBinding.webviewPagerViewpager.adapter = null
        super.onDestroyView()
    }

    override fun onStart() {
        viewBinding.webviewPagerViewpager.registerOnPageChangeCallback(pageChangeListener)
        super.onStart()
    }

    override fun onStop() {
        viewBinding.webviewPagerViewpager.unregisterOnPageChangeCallback(pageChangeListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setupBottomNavigation(
            viewBinding.navigationBottomWebviewPager,
            BottomNavigationItem.ChildOf(BottomNavigationItem.Home)
        )
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
                        viewBinding.webviewPagerViewpager.setCurrentItem(it, false)
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
        return viewBinding.webviewPagerViewpager.currentItem
    }

    private suspend fun getSupposedPagerPosition(): Int? {
        val adapter = (viewBinding.webviewPagerViewpager.adapter as? SectionPagerAdapter)
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
        val sectionsStubs = (viewBinding.webviewPagerViewpager.adapter as? SectionPagerAdapter)?.sectionStubs ?: return
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
            viewBinding.sectionPagerFabHelp.hide()
        } else {
            if (showFab) {
                viewBinding.sectionPagerFabHelp.show()
            }
            if (sectionsStubs.isEmpty()) return
            val lastSection = try {
                lastPage?.let { sectionsStubs[it] }
            } catch (ioob: IndexOutOfBoundsException) {
                log.error("could not get section of position $lastPage. ${ioob.message}")
                return
            }
            val lastWasAdvertisement = lastSection?.type == SectionType.advertisement
            val lastWasPodcast = lastSection?.type == SectionType.podcast
            if (lastWasAdvertisement || lastWasPodcast) {
                drawerAndLogoViewModel.showLogo()
            }
        }
    }

    private fun setupDrawerLogoGhost() {
        viewBinding.sectionPagerDrawerLogoGhost.setOnClickListener {
            tracker.trackDrawerOpenEvent(dragged = false)
            drawerAndLogoViewModel.openDrawer()
        }
    }

    private fun showCoachMarks() {
        val tazLogoCoachMark = TazLogoCoachMark.create(viewBinding.sectionPagerDrawerLogoGhost)
        val sectionBookmarkCoachMark = SectionBookmarkCoachMark()
        val sectionPlaylistCoachMark = SectionPlaylistCoachMark()
        val coachMarks = listOf(
            tazLogoCoachMark,
            sectionBookmarkCoachMark,
            sectionPlaylistCoachMark,
        )
        CoachMarkDialog.create(coachMarks).show(childFragmentManager, CoachMarkDialog.TAG)
    }
}
