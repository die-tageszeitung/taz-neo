package de.taz.app.android.ui.home.page.coverflow


import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper
import de.taz.app.android.R
import de.taz.app.android.databinding.FragmentCoverflowBinding
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import de.taz.app.android.ui.home.page.IssueFeedFragment
import de.taz.app.android.ui.main.MainActivity
import kotlinx.coroutines.*
import java.util.*

class CoverflowFragment : IssueFeedFragment<FragmentCoverflowBinding>() {

    override lateinit var adapter: IssueFeedAdapter

    private val snapHelper = GravitySnapHelper(Gravity.CENTER)
    private val onScrollListener = CoverFlowOnScrollListener(this, snapHelper)

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
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.pdfModeLiveData.distinctUntilChanged().observe(viewLifecycleOwner) {
                    // redraw all visible views
                    viewBinding.fragmentCoverFlowGrid.adapter?.notifyDataSetChanged()
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

        viewModel.feed.observeDistinct(this) { feed ->
            val fresh = !::adapter.isInitialized
            val requestManager = Glide.with(this)
            val itemLayout = if (viewModel.pdfModeLiveData.value == true) {
                R.layout.fragment_cover_flow_frontpage_item
            } else {
                R.layout.fragment_cover_flow_moment_item
            }
            adapter = CoverflowAdapter(
                this,
                itemLayout,
                feed,
                requestManager,
                CoverflowCoverViewActionListener(this@CoverflowFragment)
            )
            grid.adapter = adapter
            // If fragment was just constructed skip to issue in intent
            if (fresh && savedInstanceState == null) {
                initialIssueDisplay?.let { skipToPublication(it) } ?: skipToHome()
            }
        }
        grid.addOnScrollListener(onScrollListener)
    }


    override fun onResume() {
        super.onResume()
        viewModel.currentDate.observe(this) { updateUIForCurrentDate() }
        viewModel.feed.observe(this) { updateUIForCurrentDate() }
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
     */
    private fun updateUIForCurrentDate() {
        val date = viewModel.currentDate.value
        val feed = viewModel.feed.value
        // don't change UI if date is already used
        if (currentlyFocusedDate == date || date == null || feed == null) {
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

        val nextPosition = adapter.getPosition(date)
        skipToPositionIfNecessary(nextPosition)
        // set date text
        this.date.text = DateHelper.dateToLongLocalizedString(date)
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
            grid.stopScroll()
            grid.smoothScrollToPosition(position)
            grid.layoutManager?.scrollToPosition(position)
            snapHelper.scrollToPosition(position)
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
        viewModel.feed.value?.issueMaxDate?.let {
            skipToDate(simpleDateFormat.parse(it)!!)
        }
    }

    private fun skipToPublication(issueKey: IssuePublication) {
        simpleDateFormat.parse(issueKey.date)?.let {
            skipToDate(it)
        }
    }
    // endregion


    fun setTextAlpha(alpha: Float) {
        dateDownloadWrapper.alpha = alpha
    }

    fun getHomeFragment(): HomeFragment = (parentFragment as HomeFragment)

    private fun openDatePicker() = showBottomSheet(DatePickerFragment())

}