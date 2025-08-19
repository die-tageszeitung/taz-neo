package de.taz.app.android.ui.home.page.coverflow


import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper
import de.taz.app.android.BuildConfig
import de.taz.app.android.COVERFLOW_MAX_SMOOTH_SCROLL_DISTANCE
import de.taz.app.android.R
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentCoverflowBinding
import de.taz.app.android.monkey.setDefaultInsets
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.bottomSheet.HomePresentationBottomSheet
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.IssueFeedFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.math.abs

const val KEY_POSITION = "KEY_POSITION"

class CoverflowFragment : IssueFeedFragment<FragmentCoverflowBinding>() {
    private val log by Log

    private val coverFlowOnScrollListenerViewModel: CoverFlowOnScrollListener.ViewModel by viewModels()

    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var authHelper: AuthHelper
    private lateinit var tracker: Tracker

    private val snapHelper = GravitySnapHelper(Gravity.CENTER)
    private val onScrollListener by lazy {
        CoverFlowOnScrollListener(coverFlowOnScrollListenerViewModel, snapHelper)
    }

    private var downloadObserver: DownloadObserver? = null
    private var initialIssueDisplay: AbstractIssuePublication? = null
    private var firstTimeFragmentIsShown: Boolean = true
    private var isLandscape = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If this is mounted on MainActivity with ISSUE_KEY extra skip to that issue on creation
        initialIssueDisplay =
            requireActivity().intent.getParcelableExtra(MainActivity.KEY_ISSUE_PUBLICATION)

        observeScrollViewModel()
        observePdfMode()
        maybeShowLoginButton()
    }

    /**
     * redraw covers if pdfMode changes and track the changes
     */
    @SuppressLint("NotifyDataSetChanged")
    private fun observePdfMode() {
        // redraw pdfMode if changes after initial draw
        viewModel.pdfModeFlow
            .drop(1)
            .onEach {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    viewBinding.fragmentCoverFlowGrid.adapter?.notifyDataSetChanged()
                }
            }.launchIn(lifecycleScope)

        // once when initiated and whenever pdfMode changes track CoverFlow
        viewModel.pdfModeFlow
            .onEach { pdfMode ->
                withContext(Dispatchers.Default) {
                    tracker.trackCoverflowScreen(pdfMode)
                }
            }.launchIn(lifecycleScope)
    }

    /**
     * react to scrolling
     */
    private fun observeScrollViewModel() {
        // adjust date alpha when scrolling
        coverFlowOnScrollListenerViewModel.dateAlpha
            .flowWithLifecycle(lifecycle)
            .onEach {
                viewBinding.fragmentCoverFlowDateDownloadWrapper.alpha = it
            }.launchIn(lifecycleScope)

        // adjust date when scrolling
        coverFlowOnScrollListenerViewModel.currentDate
            .flowWithLifecycle(lifecycle)
            .filterNotNull()
            .onEach { date ->
                updateUIForCurrentDate()
            }.launchIn(lifecycleScope)

        // trigger refresh when scrolling into the left void
        coverFlowOnScrollListenerViewModel.refresh
            .flowWithLifecycle(lifecycle)
            .onEach {
                getHomeFragment().refresh()
            }.launchIn(lifecycleScope)
    }

    /**
     * hide or show login button depending on auth status
     */
    private fun maybeShowLoginButton() {
        // create new flow that indicates if waiting for mail or logged in
        combine(
            authHelper.isPollingForConfirmationEmail.asFlow(),
            authHelper.isLoggedInFlow,
        ) { isPolling, isLoggedIn -> isPolling || isLoggedIn }
            .flowWithLifecycle(lifecycle)
            .onEach {
                viewBinding.homeLoginButton.visibility = if (it) View.GONE else View.VISIBLE
            }.launchIn(lifecycleScope)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // initialize the views
        viewBinding.apply {
            // ensure padding is correct
            root.setDefaultInsets()

            fragmentCoverFlowGrid.apply {
                // make it bouncy
                edgeEffectFactory = BouncyEdgeEffect.Factory

                // ensure the CoverFlow is drawn
                layoutManager = CoverFlowLinearLayoutManager(requireContext(), this)

                // make accessible
                setAccessibilityDelegateCompat(
                    CoverFlowAccessibilityDelegate(
                        this,
                        fragmentCoverFlowDate.text
                    )
                )

                // add scroll logic
                addOnScrollListener(onScrollListener)
            }

            snapHelper.apply {
                attachToRecyclerView(fragmentCoverFlowGrid)
                maxFlingSizeFraction = 0.75f
                snapLastItem = true

            }

            // set onClickListener
            representation.setOnClickListener {
                HomePresentationBottomSheet().show(parentFragmentManager)
            }
            fragmentCoverFlowDate.setOnClickListener { openDatePicker() }
            fragmentCoverFlowCalendar.setOnClickListener { openDatePicker() }
            fragmentCoverFlowIconGoPrevious.setOnClickListener { goToPreviousIssue() }
            fragmentCoverFlowIconGoNext.setOnClickListener { goToNextIssue() }
            homeLoginButton.setOnClickListener { showLoginBottomSheet() }
        }

        viewModel.feed
            .onEach { feed ->
                // Store current adapter state before setting some new one
                val prevMomentDate = coverFlowOnScrollListenerViewModel.currentDate.value
                val prevHomeMomentDate = adapter?.getItem(0)?.date
                val initialAdapter = adapter == null

                val requestManager = Glide.with(requireParentFragment())
                val adapter = CoverflowAdapter(
                    this,
                    R.layout.fragment_cover_flow_item,
                    feed,
                    requestManager,
                    CoverflowCoverViewActionListener(this@CoverflowFragment)
                )
                this.adapter = adapter
                viewBinding.fragmentCoverFlowGrid.adapter = adapter
                setProperMargin(adapter)

                // If this is the first adapter to be assigned, but the Fragment is just restored from the persisted store,
                // we let Android restore the scroll position. This might work as long as the feed did not change.
                val restoreFromPersistedState = initialAdapter && savedInstanceState != null

                if (!restoreFromPersistedState) {
                    if (initialAdapter) {
                        // The adapter has not been set yet. This is the first time this observer is
                        // called and there is no previous adapter/visible coverflow yet
                        val initialIssueDisplayDate = initialIssueDisplay?.date
                        if (initialIssueDisplayDate != null) {
                            viewModel.requestDateFocus(initialIssueDisplayDate)
                        } else {
                            viewModel.requestNewestDateFocus()
                        }
                    } else {
                        // The adapter is already initialized. This is an update which might break our scroll position.
                        val wasHomeSelected =
                            prevHomeMomentDate != null && prevHomeMomentDate == prevMomentDate

                        if (!wasHomeSelected && prevMomentDate != null) {
                            viewModel.requestDateFocus(prevMomentDate)
                        } else {
                            viewModel.requestNewestDateFocus()
                        }
                    }
                } else {
                    val oldPosition = savedInstanceState?.getInt(KEY_POSITION, -1) ?: -1
                    if (oldPosition > 0) {
                        viewBinding.fragmentCoverFlowGrid.scrollToPosition(oldPosition)

                        snapHelper.updateSnap(true, true)
                    }
                }

                isLandscape =
                    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                updateUIForCurrentDate()
            }.launchIn(lifecycleScope)

        // scroll to date if focus is requested
        viewModel.requestDateFocus.onEach { date ->
            adapter?.getPosition(date)?.let { position ->
                skipToPositionIfNecessary(position)
            }
        }.launchIn(lifecycleScope)
    }

    override fun onDestroyView() {
        snapHelper.attachToRecyclerView(null)
        viewBinding.fragmentCoverFlowGrid.adapter = null
        viewBinding.fragmentCoverFlowGrid.removeOnScrollListener(onScrollListener)
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        coverFlowOnScrollListenerViewModel.currentDate.value?.let {
            adapter?.getPosition(it)
        }?.let {
            outState.putInt(KEY_POSITION, it)
        }
    }

    /**
     * this function will update the date text and the download icon
     * and will skip to the right position if we are not already there
     */
    private suspend fun updateUIForCurrentDate() {
        val date = coverFlowOnScrollListenerViewModel.currentDate.value
        val feed = viewModel.feed.first()
        val adapter = adapter

        if (date == null || adapter == null) {
            return
        }

        val nextPosition = adapter.getPosition(date)

        // Hide the left arrow when on start
        viewBinding.fragmentCoverFlowIconGoPrevious.isVisible = nextPosition > 0

        // Hide the right arrow when on end
        viewBinding.fragmentCoverFlowIconGoNext.isVisible = nextPosition < adapter.itemCount - 1

        // show date and download icon
        viewBinding.fragmentCoverFlowDateDownloadWrapper.isVisible = true

        val item = adapter.getItem(nextPosition)

        // stop old downloadObserver
        downloadObserver?.stopObserving()

        lifecycleScope.launch {
            val issuePublication = if (viewModel.getPdfMode()) {
                IssuePublicationWithPages(feed.name, simpleDateFormat.format(date))
            } else {
                IssuePublication(feed.name, simpleDateFormat.format(date))
            }

            // start new downloadObserver
            downloadObserver = DownloadObserver(
                this@CoverflowFragment,
                issuePublication,
                viewBinding.fragmentCoverflowMomentDownload,
                viewBinding.fragmentCoverflowMomentDownloadFinished,
                viewBinding.fragmentCoverflowMomentDownloading,
            ).apply {
                startObserving()
            }
        }
        val isTabletMode = requireContext().resources.getBoolean(R.bool.isTablet)

        val isLandscapeOnSmartphone = isLandscape && !isTabletMode
        // set date text
        viewBinding.fragmentCoverFlowDate.text = when {
            BuildConfig.IS_LMD ->
                DateHelper.dateToLocalizedMonthAndYearString(date)

            item?.validity != null && !isLandscapeOnSmartphone ->
                DateHelper.dateToWeekNotation(
                    item.date,
                    item.validity
                )

            item?.validity != null && isLandscapeOnSmartphone ->
                DateHelper.dateToShortRangeString(
                    item.date,
                    item.validity
                )

            item != null && !isLandscapeOnSmartphone ->
                DateHelper.dateToLongLocalizedLowercaseString(item.date)

            item != null && isLandscapeOnSmartphone ->
                DateHelper.dateToMediumLocalizedString(item.date)

            else -> {
                // The date itself is not found anymore. This is weird. Log an error but show the requested date
                log.warn("The date $date was not found in the feeds publication dates")
                DateHelper.dateToLongLocalizedLowercaseString(date)
            }
        }
        // set accessibility for date picker:
        viewBinding.fragmentCoverFlowDate.contentDescription = resources.getString(
            R.string.fragment_cover_flow_date_content_description,
            viewBinding.fragmentCoverFlowDate.text
        )
    }

    /**
     * Sometimes in the carousel the items are not snapped in the center correctly.
     * That happens when the [adapter]s [position] is different from the [GravitySnapHelper]s or
     * the first time the fragment is drawn (determined by [firstTimeFragmentIsShown]).
     */
    private fun skipToPositionIfNecessary(position: Int) {
        // nextPosition could already be correct because of scrolling if not skip there
        if (position != snapHelper.currentSnappedPosition || firstTimeFragmentIsShown) {
            firstTimeFragmentIsShown = false

            // Stop any scroll that might still be going on.
            // Either from a previous scrollTo call or a user fling.
            viewBinding.fragmentCoverFlowGrid.stopScroll()

            val shouldSmoothScroll =
                abs(position - snapHelper.currentSnappedPosition) <= COVERFLOW_MAX_SMOOTH_SCROLL_DISTANCE

            // We are using the RecycleViews default scrolling mechanism and rely on the
            // snapHelpers observing to do the final snapping.
            if (shouldSmoothScroll) {
                viewBinding.fragmentCoverFlowGrid.smoothScrollToPosition(position)
            } else {
                viewBinding.fragmentCoverFlowGrid.scrollToPosition(position)
            }
        }
    }

    private fun getHomeFragment(): HomeFragment = (parentFragment as HomeFragment)

    private fun openDatePicker() {
        DatePickerFragment.newInstance(
            coverFlowOnScrollListenerViewModel.currentDate.value ?: Date()
        )
            .show(childFragmentManager, DatePickerFragment.TAG)
    }

    private fun setProperMargin(adapter: CoverflowAdapter) {

        val coverItemPadding = resources.getDimensionPixelSize(R.dimen.cover_item_padding)
        val momentWidth = adapter.calculateViewHolderWidth() - 2 * coverItemPadding
        var newMarginEnd = (resources.displayMetrics.widthPixels - momentWidth) / 2

        lifecycleScope.launch(Dispatchers.Main) {
            val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
            if (extraPadding > 0 && isLandscape) {
                val halfExtraPaddingInPx =
                    (extraPadding * resources.displayMetrics.density / 2).toInt()
                newMarginEnd = newMarginEnd - halfExtraPaddingInPx
            }
            // snap cover flow after setting margin
            // TODO check where there might be place where this actually makes sense
            snapHelper.updateSnap(true, true)
        }
    }

    private fun goToPreviousIssue() {
        viewBinding.fragmentCoverFlowGrid.smoothScrollToPosition(snapHelper.currentSnappedPosition - 1)
    }

    private fun goToNextIssue() {
        viewBinding.fragmentCoverFlowGrid.smoothScrollToPosition(snapHelper.currentSnappedPosition + 1)
    }
}