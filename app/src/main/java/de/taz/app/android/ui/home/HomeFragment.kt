package de.taz.app.android.ui.home

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.withStarted
import de.taz.app.android.BuildConfig
import de.taz.app.android.R
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.coachMarks.ArchiveContinueReadCoachMark
import de.taz.app.android.coachMarks.ArchiveDatePickerCoachMark
import de.taz.app.android.coachMarks.ArchiveDownloadCoachMark
import de.taz.app.android.coachMarks.BaseCoachMark
import de.taz.app.android.coachMarks.CoachMarkDialog
import de.taz.app.android.coachMarks.CoverflowContinueReadCoachMark
import de.taz.app.android.coachMarks.CoverflowDatePickerCoachMark
import de.taz.app.android.coachMarks.CoverflowDownloadCoachMark
import de.taz.app.android.coachMarks.HomeBookmarksCoachMark
import de.taz.app.android.coachMarks.HomeHomeCoachMark
import de.taz.app.android.coachMarks.HomePlaylistCoachMark
import de.taz.app.android.coachMarks.HomePresentationCoachMark
import de.taz.app.android.coachMarks.HomeSearchCoachMark
import de.taz.app.android.coachMarks.HomeSettingsCoachMark
import de.taz.app.android.content.FeedService
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentHomeBinding
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.home.page.IssueFeedViewModel
import de.taz.app.android.ui.home.page.archive.ArchiveFragment
import de.taz.app.android.ui.home.page.coverflow.CoverflowFragment
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import de.taz.app.android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Date


class HomeFragment : BaseMainFragment<FragmentHomeBinding>() {

    enum class State {
        COVERFLOW,
        ARCHIVE,
    }

    val log by Log

    var onHome: Boolean = true
    private var refreshJob: Job? = null


    private lateinit var feedService: FeedService
    private lateinit var toastHelper: ToastHelper
    private lateinit var tracker: Tracker
    private lateinit var generalDataStore: GeneralDataStore

    private val issueFeedViewModel: IssueFeedViewModel by activityViewModels()

    override fun onAttach(context: Context) {
        super.onAttach(context)
        feedService = FeedService.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // allow or forbid user to drag refreshView
        // before user interacts we are in resumed state
        issueFeedViewModel.refreshViewEnabled
            .flowWithLifecycle(lifecycle, Lifecycle.State.RESUMED)
            .onEach {
                viewBinding.coverflowRefreshLayout.isEnabled = it
            }.launchIn(lifecycleScope)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.apply {
            coverflowRefreshLayout.apply {
                setOnRefreshListener {
                    refreshFeedDebounced()
                }
                reduceDragSensitivity(10)
            }
        }

        // show Fragment if state changes and lifecycle in STARTED
        generalDataStore.homeFragmentState.asFlow()
            .onEach {
                withStarted {
                    showFragmentForState(it)
                }
            }.launchIn(lifecycleScope)

        setupFAB()
    }

    /**
     * On edge to edge we need to properly update the margins of the FAB:
     */
    private fun setupFAB() {
        ViewCompat.setOnApplyWindowInsetsListener(viewBinding.fabHelp) { v, windowInsets ->
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
        viewBinding.fabHelp.setOnClickListener {
            log.verbose("show coach marks in home")
            lifecycleScope.launch {
                showCoachMarks()
            }
        }

        combine(
            generalDataStore.helpFabEnabled.asFlow(),
            issueFeedViewModel.appBarVisible
        ) { helpEnabled, appBarVisible -> helpEnabled && appBarVisible }
            .flowWithLifecycle(lifecycle)
            .onEach {
                if (it) {
                    viewBinding.fabHelp.show()
                } else {
                    viewBinding.fabHelp.hide()
                }
            }.launchIn(lifecycleScope)
    }

    private fun showFragmentForState(state: State) {
        val oldFragment = childFragmentManager.findFragmentByTag(state.name)

        if (childFragmentManager.fragments.find { it.tag == state.name } != null)
            return

        val transaction = childFragmentManager.beginTransaction()

        childFragmentManager.fragments.forEach {
            transaction.hide(it)
        }

        if (oldFragment == null) {
            // add fragment if it does not exist
            val fragment = when (state) {
                State.ARCHIVE -> ArchiveFragment()
                State.COVERFLOW -> CoverflowFragment()
            }
            log.debug("adding fragment for state: ${state.name}")

            transaction
                .add(R.id.home_fragment, fragment, state.name)
                .addToBackStack(state.name)
        } else {
            log.debug("showing old fragment for state: ${state.name}")
            transaction
                .show(oldFragment)
        }
        transaction.commit()
    }

    private fun refreshFeedDebounced() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val start = Date().time
                refreshFeed()
                val end = Date().time
                // show animation at least 1000 ms so it looks smoother
                delay(1000L - (end - start))
                hideRefreshLoadingIcon()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setupBottomNavigation(
            viewBinding.navigationBottom,
            BottomNavigationItem.Home
        )
    }

    private suspend fun refreshFeed() {
        try {
            val feedService = FeedService.getInstance(requireContext().applicationContext)
            feedService.refreshFeed(BuildConfig.DISPLAYED_FEED)
            issueFeedViewModel.forceRefresh()
        } catch (e: ConnectivityException.NoInternetException) {
            ToastHelper.getInstance(requireContext().applicationContext)
                .showNoConnectionToast()
        } catch (e: ConnectivityException.ImplementationException) {
            ToastHelper.getInstance(requireContext().applicationContext)
                .showSomethingWentWrongToast()
        }
    }

    private fun hideRefreshLoadingIcon() {
        viewBinding.coverflowRefreshLayout.isRefreshing = false
    }

    override fun onDestroyView() {
        refreshJob?.cancel()
        refreshJob = null
        super.onDestroyView()
    }

    fun skipToHome() = lifecycleScope.launch {
        issueFeedViewModel.requestNewestDateFocus()
    }

    fun refresh() = viewBinding.coverflowRefreshLayout.setRefreshingWithCallback(true)

    private suspend fun showCoachMarks() {
        val homePresentationCoachMark = HomePresentationCoachMark.create(
            viewBinding.root
                .findViewById(R.id.representation)!!
        )

        val coachMarks = mutableListOf<BaseCoachMark>(
            homePresentationCoachMark
        )
        val homeHomeCoachMark = HomeHomeCoachMark.create(
            viewBinding.root
                .findViewById<View?>(R.id.bottom_navigation_action_home)!!
        )
        val homeBookmarksCoachMark = HomeBookmarksCoachMark.create(
            viewBinding.root
                .findViewById<View?>(R.id.bottom_navigation_action_bookmark)!!
        )
        val homePlaylistCoachMark = HomePlaylistCoachMark.create(
            viewBinding.root
                .findViewById<View?>(R.id.bottom_navigation_action_playlist)!!
        )
        val homeSearchCoachMark = HomeSearchCoachMark.create(
            viewBinding.root
                .findViewById<View?>(R.id.bottom_navigation_action_search)!!
        )
        val homeSettingsCoachMark = HomeSettingsCoachMark.create(
            viewBinding.root
                .findViewById<View?>(R.id.bottom_navigation_action_settings)!!
        )
        val genericHomeCoachMarks = listOf(
            homeHomeCoachMark,
            homeBookmarksCoachMark,
            homePlaylistCoachMark,
            homeSearchCoachMark,
            homeSettingsCoachMark,
        )
        when (generalDataStore.homeFragmentState.get()) {
            State.ARCHIVE -> {
                val archiveDatePickerCoachMark = ArchiveDatePickerCoachMark.create(
                    viewBinding.root
                        .findViewById(R.id.calendar)!!
                )
                val archiveDownloadCoachMark = ArchiveDownloadCoachMark()
                val continueReadCoachMark = ArchiveContinueReadCoachMark()

                val archiveCoachMarks = listOf(
                    archiveDatePickerCoachMark,
                    archiveDownloadCoachMark,
                    continueReadCoachMark
                )
                coachMarks.addAll(
                    archiveCoachMarks
                )
            }

            State.COVERFLOW -> {
                val downloadIconWrapperView =
                    viewBinding.root.findViewById<ConstraintLayout>(R.id.fragment_coverflow_moment_download_touch_area)!!

                val coverflowDownloadCoachMark = CoverflowDownloadCoachMark.create(
                    downloadIconWrapperView
                )
                val coverflowContinueReadCoachMark = CoverflowContinueReadCoachMark.create(
                    downloadIconWrapperView
                )
                val coverflowDatePickerCoachMark = CoverflowDatePickerCoachMark.create(
                    viewBinding.root.findViewById(R.id.fragment_cover_flow_calendar)
                )

                val coverFlowCoachMarks = listOf(
                    coverflowDownloadCoachMark,
                    coverflowContinueReadCoachMark,
                    coverflowDatePickerCoachMark,
                )
                coachMarks.addAll(
                    coverFlowCoachMarks
                )
            }
        }
        coachMarks.addAll(genericHomeCoachMarks)
        CoachMarkDialog.create(coachMarks).show(childFragmentManager, CoachMarkDialog.TAG)
    }
}