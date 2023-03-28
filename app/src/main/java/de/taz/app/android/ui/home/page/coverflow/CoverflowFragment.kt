package de.taz.app.android.ui.home.page.coverflow


import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper
import de.taz.app.android.BuildConfig
import de.taz.app.android.COVERFLOW_MAX_SMOOTH_SCROLL_DISTANCE
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentCoverflowBinding
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.IssueFeedFragment
import de.taz.app.android.ui.login.LoginActivity
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import de.taz.app.android.util.validation.EmailValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.abs

class CoverflowFragment() : IssueFeedFragment<FragmentCoverflowBinding>() {
    private val log by Log

    private lateinit var authHelper: AuthHelper
    private val snapHelper = GravitySnapHelper(Gravity.CENTER)
    private val onScrollListener = CoverFlowOnScrollListener(this, snapHelper)
    private val emailValidator = EmailValidator()

    private var downloadObserver: DownloadObserver? = null
    private var initialIssueDisplay: IssuePublication? = null
    private var currentlyFocusedDate: Date? = null
    private var firstTimeFragmentIsShown: Boolean = true

    private val grid by lazy { viewBinding.fragmentCoverFlowGrid }
    private val toArchive by lazy { viewBinding.fragmentCoverFlowToArchive }
    private val date by lazy { viewBinding.fragmentCoverFlowDate }
    private val momentDownload by lazy { viewBinding.fragmentCoverflowMomentDownload }
    private val momentDownloadFinished by lazy { viewBinding.fragmentCoverflowMomentDownloadFinished }
    private val momentDownloading by lazy { viewBinding.fragmentCoverflowMomentDownloading }
    private val dateDownloadWrapper by lazy { viewBinding.fragmentCoverFlowDateDownloadWrapper }

    // region lifecycle functions
    @SuppressLint("NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If this is mounted on MainActivity with ISSUE_KEY extra skip to that issue on creation
        initialIssueDisplay =
            requireActivity().intent.getParcelableExtra(MainActivity.KEY_ISSUE_PUBLICATION)
        authHelper = AuthHelper.getInstance(requireContext().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.pdfModeLiveData.observeDistinctIgnoreFirst(viewLifecycleOwner) {
                    // redraw all visible views
                    viewBinding.fragmentCoverFlowGrid.adapter?.notifyDataSetChanged()
                    updateUIForCurrentDate(forceStartDownloadObserver = true)
                }
            }
        }

        grid.layoutManager =
            CoverFlowLinearLayoutManager(requireContext(), grid)

        snapHelper.apply {
            attachToRecyclerView(grid)
            maxFlingSizeFraction = 0.75f
            snapLastItem = true
        }

        toArchive.setOnClickListener { getHomeFragment().showArchive() }
        date.setOnClickListener { openDatePicker() }

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
            grid.adapter = adapter

            // If this is the first adapter to be assigned, but the Fragment is just restored from the persisted store,
            // we let Android restore the scroll position. This might work as long as the feed did not change.
            // FIXME(johannes): test if it actually works as a new adapter is assigned
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
            updateUIForCurrentDate()
        }
        viewModel.currentDate.observe(viewLifecycleOwner) { updateUIForCurrentDate() }
        authHelper.email.asLiveData().observeDistinct(viewLifecycleOwner) {
            determineWhetherToShowLoginButton(it)
        }
        grid.addOnScrollListener(onScrollListener)
    }

    override fun onDestroyView() {
        snapHelper.attachToRecyclerView(null)
        grid.adapter = null
        grid.removeOnScrollListener(onScrollListener)
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

        // if we have position 0 we are on teh actual issue, so set the home icon to filled:
        getHomeFragment().setHomeIconFilled(nextPosition == 0)

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
            momentDownload,
            momentDownloadFinished,
            momentDownloading
        ).apply {
            startObserving()
        }

        // set date text
        this.date.text = when {
            BuildConfig.IS_LMD ->
                DateHelper.dateToLocalizedMonthAndYearString(date)
            item != null && item.validity != null ->
                DateHelper.dateToWeekNotation(
                    item.date,
                    item.validity
                )
            item != null ->
                DateHelper.dateToLongLocalizedLowercaseString(item.date)
            else -> {
                // The date itself is not found anymore. This is weird. Log an error but show the requested date
                log.warn("The date $date was not found in the feeds publication dates")
                DateHelper.dateToLongLocalizedLowercaseString(date)
            }
        }
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
            grid.stopScroll()

            val shouldSmoothScroll =
                abs(position - snapHelper.currentSnappedPosition) <= COVERFLOW_MAX_SMOOTH_SCROLL_DISTANCE

            // We are using the RecycleViews default scrolling mechanism and rely on the
            // snapHelpers observing to do the final snapping.
            if (shouldSmoothScroll) {
                grid.smoothScrollToPosition(position)
            } else {
                grid.scrollToPosition(position)
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
        getHomeFragment().setHomeIconFilled()
        viewModel.feed.value?.publicationDates?.firstOrNull()?.let {
            skipToDate(it.date)
        }
    }

    private fun skipToPublication(issueKey: IssuePublication) {
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
                        activity?.startActivity(
                            Intent(activity, LoginActivity::class.java)
                        )
                    }
                }

            }
        }
    }

    fun setTextAlpha(alpha: Float) {
        dateDownloadWrapper.alpha = alpha
    }

    fun getHomeFragment(): HomeFragment = (parentFragment as HomeFragment)

    private fun openDatePicker() = showBottomSheet(DatePickerFragment())

}