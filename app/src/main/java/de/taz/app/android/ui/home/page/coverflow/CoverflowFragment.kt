package de.taz.app.android.ui.home.page.coverflow


import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate
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
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.IssueFeedFragment
import de.taz.app.android.ui.login.LoginBottomSheetFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import de.taz.app.android.util.validation.EmailValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.abs

class CoverflowFragment : IssueFeedFragment<FragmentCoverflowBinding>() {
    private val log by Log

    private lateinit var authHelper: AuthHelper
    private lateinit var generalDataStore: GeneralDataStore

    private val snapHelper = GravitySnapHelper(Gravity.CENTER)
    private val onScrollListener = CoverFlowOnScrollListener(this, snapHelper)
    private val emailValidator = EmailValidator()

    private var downloadObserver: DownloadObserver? = null
    private var initialIssueDisplay: AbstractIssuePublication? = null
    private var currentlyFocusedDate: Date? = null
    private var firstTimeFragmentIsShown: Boolean = true
    private var isLandscape = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        authHelper = AuthHelper.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    // region lifecycle functions
    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If this is mounted on MainActivity with ISSUE_KEY extra skip to that issue on creation
        initialIssueDisplay =
            requireActivity().intent.getParcelableExtra(MainActivity.KEY_ISSUE_PUBLICATION)

        observePdfMode()
    }

    private fun observePdfMode() {
        val tracker = Tracker.getInstance(requireContext().applicationContext)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                // redraw pdfMode if changes after initial draw
                viewModel.pdfMode.drop(1).onEach {
                    viewBinding.fragmentCoverFlowGrid.adapter?.notifyDataSetChanged()
                }.launchIn(lifecycleScope)

                // once when initiated and whenever pdfMode changes track CoverFlow
                viewModel.pdfMode.onEach { pdfMode ->
                    tracker.trackCoverflowScreen(pdfMode)
                }.launchIn(lifecycleScope)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.apply {
            // ensure padding is correct
            root.setDefaultInsets()

            // make it bouncy
            fragmentCoverFlowGrid.apply {
                edgeEffectFactory = BouncyEdgeEffect.Factory
                layoutManager = CoverFlowLinearLayoutManager(requireContext(), this@apply)

                setAccessibilityDelegateCompat(object :
                    RecyclerViewAccessibilityDelegate(this@apply) {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: AccessibilityNodeInfoCompat
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            host.accessibilityPaneTitle = fragmentCoverFlowDate.text
                        }
                        host.requestFocus()
                        info.setCollectionInfo(null)
                    }
                })
                addOnScrollListener(onScrollListener)
            }

            snapHelper.apply {
                attachToRecyclerView(fragmentCoverFlowGrid)
                maxFlingSizeFraction = 0.75f
                snapLastItem = true
            }

            fragmentCoverFlowToArchive.setOnClickListener { getHomeFragment().showArchive() }
            fragmentCoverFlowDate.setOnClickListener { openDatePicker() }

            fragmentCoverFlowIconGoPrevious.setOnClickListener {
                goToPreviousIssue()
            }

            fragmentCoverFlowIconGoNext.setOnClickListener {
                goToNextIssue()
            }
        }

        viewModel.feed.observe(viewLifecycleOwner) { feed ->
            // Store current adapter state before setting some new one
            val prevMomentDate = viewModel.currentDate.value
            val prevHomeMomentDate = adapter?.getItem(0)?.date
            val initialAdapter = adapter == null

            val requestManager = Glide.with(this)
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
                    if (initialIssueDisplay != null) {
                        skipToPublication(requireNotNull(initialIssueDisplay))
                    } else {
                        skipToHome()
                    }
                } else {
                    // The adapter is already initialized. This is an update which might break our scroll position.
                    val wasHomeSelected =
                        prevHomeMomentDate != null && prevHomeMomentDate == prevMomentDate

                    if (!wasHomeSelected && prevMomentDate != null) {
                        skipToDate(prevMomentDate)
                    } else {
                        skipToHome()
                    }
                }
            }

            // Force updating the UI when the feed changes by resetting the currently focused date
            currentlyFocusedDate = null
            isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            updateUIForCurrentDate()
        }
        viewModel.currentDate.observe(viewLifecycleOwner) { updateUIForCurrentDate() }
        authHelper.email.asLiveData().distinctUntilChanged().observe(viewLifecycleOwner) {
            determineWhetherToShowLoginButton(it)
        }
    }

    override fun onDestroyView() {
        snapHelper.attachToRecyclerView(null)
        viewBinding.fragmentCoverFlowGrid.adapter = null
        viewBinding.fragmentCoverFlowGrid.removeOnScrollListener(onScrollListener)
        super.onDestroyView()
    }
    // endregion

    // region UI update function
    /**
     * this function will update the date text and the download icon
     * and will skip to the right position if we are not already there
     * @param forceStartDownloadObserver - Boolean indication it was a pdf mode switch. Then do not return too early.
     */
    private fun updateUIForCurrentDate(forceStartDownloadObserver: Boolean = false) {
        val date = viewModel.currentDate.value
        val feed = viewModel.feed.value
        val adapter = adapter

        if (date == null || feed == null || adapter == null) {
            return
        }

        // Always check if we need to snap to the date's position - even if the date did not change.
        // This prevents a bug that results in non-snapping behavior when a user logged in.
        val nextPosition = adapter.getPosition(date)
        skipToPositionIfNecessary(nextPosition)

        // Hide the left arrow when on start
        viewBinding.fragmentCoverFlowIconGoPrevious.isVisible = nextPosition > 0

        // Hide the right arrow when on end
        viewBinding.fragmentCoverFlowIconGoNext.isVisible = nextPosition < adapter.itemCount - 1

        val item = adapter.getItem(nextPosition)
        if (currentlyFocusedDate == date && !forceStartDownloadObserver) {
            return
        }
        currentlyFocusedDate = date

        // stop old downloadObserver
        downloadObserver?.stopObserving()

        val issuePublication = if (viewModel.pdfModeLiveData.value == true) {
            IssuePublicationWithPages(feed.name, simpleDateFormat.format(date))
        } else {
            IssuePublication(feed.name, simpleDateFormat.format(date))
        }

        // start new downloadObserver
        downloadObserver = DownloadObserver(
            this,
            issuePublication,
            viewBinding.fragmentCoverflowMomentDownload,
            viewBinding.fragmentCoverflowMomentDownloadFinished,
            viewBinding.fragmentCoverflowMomentDownloading,
        ).apply {
            startObserving()
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
    // endregion

    // region skip functions
    fun skipToDate(date: Date) {
        if (viewModel.currentDate.value != date)
            viewModel.currentDate.postValue(date)
    }

    fun skipToHome() {
        viewModel.feed.value?.publicationDates?.firstOrNull()?.let {
            skipToDate(it.date)
        }
    }

    private fun skipToPublication(issueKey: AbstractIssuePublication) {
        simpleDateFormat.parse(issueKey.date)?.let {
            skipToDate(it)
        }
    }
    // endregion

    /**
     * Hide the homeLoginButton if user is logged in or if we got a valid email
     * and user is waiting for confirmation mail
     */
    private fun determineWhetherToShowLoginButton(email: String) {
        val isValidEmail = emailValidator(email)
        viewBinding.apply {
            lifecycleScope.launch(Dispatchers.Main) {
                if (isValidEmail || authHelper.isLoggedIn()) {
                    homeLoginButton.visibility = View.GONE
                } else {
                    homeLoginButton.visibility = View.VISIBLE
                    homeLoginButton.setOnClickListener {
                        LoginBottomSheetFragment
                            .newInstance()
                            .show(parentFragmentManager, LoginBottomSheetFragment.TAG)
                    }
                }

            }
        }
    }

    fun setTextAlpha(alpha: Float) {
        viewBinding.fragmentCoverFlowDateDownloadWrapper.alpha = alpha
    }

    fun getHomeFragment(): HomeFragment = (parentFragment as HomeFragment)

    private fun openDatePicker() {
        DatePickerFragment().show(childFragmentManager, DatePickerFragment.TAG)
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
            viewBinding.fragmentCoverflowMomentDownloadPosition.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginEnd = newMarginEnd
            }
        }
    }

    private fun goToPreviousIssue() {
        currentlyFocusedDate?.let {
            val currentPosition = adapter?.getPosition(it)
            if (currentPosition != null) {
                viewBinding.fragmentCoverFlowGrid.smoothScrollToPosition(currentPosition - 1)
            }
        }
    }

    private fun goToNextIssue() {
        currentlyFocusedDate?.let {
            val currentPosition = adapter?.getPosition(it)
            if (currentPosition != null) {
                viewBinding.fragmentCoverFlowGrid.smoothScrollToPosition(currentPosition + 1)
            }
        }
    }
}