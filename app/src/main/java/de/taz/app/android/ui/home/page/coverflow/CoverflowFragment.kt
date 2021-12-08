package de.taz.app.android.ui.home.page.coverflow


import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper
import de.taz.app.android.R
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import de.taz.app.android.ui.home.page.IssueFeedFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_coverflow.*
import kotlinx.coroutines.*
import java.util.*

class CoverflowFragment : IssueFeedFragment(R.layout.fragment_coverflow) {

    val log by Log

    override lateinit var adapter: IssueFeedAdapter

    private val snapHelper = GravitySnapHelper(Gravity.CENTER)
    private val onScrollListener = CoverFlowOnScrollListener(this, snapHelper)

    private var downloadObserver: DownloadObserver? = null
    private var initialIssueDisplay: IssuePublication? = null

    private var currentlyFocusedDate: Date? = null
    private var currentlyBoundPosition: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If this is mounted on MainActivity with ISSUE_KEY extra skip to that issue on creation
        initialIssueDisplay =
            requireActivity().intent.getParcelableExtra(MainActivity.KEY_ISSUE_PUBLICATION)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_cover_flow_grid.layoutManager =
            CoverFlowLinearLayoutManager(requireContext(), fragment_cover_flow_grid)

        snapHelper.apply {
            attachToRecyclerView(fragment_cover_flow_grid)
            maxFlingSizeFraction = 0.75f
            snapLastItem = true
        }

        fragment_cover_flow_to_archive.setOnClickListener {
            (parentFragment as? HomeFragment)?.showArchive()
        }

        fragment_cover_flow_date.setOnClickListener {
            openDatePicker()
        }

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
            fragment_cover_flow_grid.adapter = adapter
            // If fragment was just constructed skip to issue in intent
            if (fresh && savedInstanceState == null) {
                initialIssueDisplay?.let { skipToPublication(it) } ?: skipToHome()
            } else if (fresh && savedInstanceState != null) {
                // if there is a currentdate in the viewmodel scroll to it to let it snap
                // in again after rotating
                lifecycleScope.launch {
                    skipToDate(
                        viewModel.currentDate.value ?: simpleDateFormat.parse(feed.issueMaxDate)!!
                    )
                }
            } else {
                // An update to the feed without fresh must mean there is an update!
                skipToHome()
            }
        }
    }


    override fun onResume() {
        fragment_cover_flow_grid.addOnScrollListener(onScrollListener)
        snapHelper.setSnapListener { position ->
            (parentFragment as? HomeFragment)?.apply {
                if (position == 0) {
                    setHomeIconFilled()
                } else {
                    setHomeIcon()
                }
            }

            adapter.getItem(position)?.let { date ->
                viewModel.currentDate.postValue(
                    date
                )
            }
        }
        super.onResume()
    }

    private fun openDatePicker() {
        val bottomSheet = showBottomSheet(DatePickerFragment())
        bottomSheet.dialog?.setOnDismissListener {
            viewModel.currentDate.value?.let {
                lifecycleScope.launch {
                    skipToDate(it)
                }
            }
        }
    }

    override fun onPause() {
        fragment_cover_flow_grid.removeOnScrollListener(onScrollListener)
        super.onPause()
    }


    private fun scrollToHome() {
        if (!::adapter.isInitialized) {
            return
        }
        fragment_cover_flow_grid.stopScroll()
        fragment_cover_flow_grid.layoutManager?.scrollToPosition(0)
        snapHelper.scrollToPosition(0)
        (parentFragment as? HomeFragment)?.setHomeIconFilled()
    }

    // function to update the text (with download icon)
    fun setDateTextAndDownloadObserver(date: Date) {
        if (currentlyFocusedDate == date) {
            return
        }
        currentlyFocusedDate = date
        viewModel.currentDate.postValue(date)

        downloadObserver?.stopObserving()

        downloadObserver = DownloadObserver(
            this,
            IssuePublication(viewModel.feed.value!!.name, simpleDateFormat.format(date)),
            fragment_coverflow_moment_download,
            fragment_coverflow_moment_download_finished,
            fragment_coverflow_moment_downloading
        ).apply {
            startObserving()
        }
        fragment_cover_flow_date?.text = DateHelper.dateToLongLocalizedString(date)
    }

    // this function will change the UI by skipping directly to a date
    fun skipToDate(date: Date) {
        if (!::adapter.isInitialized) {
            return
        }
        setDateTextAndDownloadObserver(date)

        currentlyBoundPosition = adapter.getPosition(date)
        currentlyBoundPosition?.let { position ->
            (fragment_cover_flow_grid.layoutManager as? LinearLayoutManager)?.apply {
                val currentPosition: Int =
                    (findFirstVisibleItemPosition() + findLastVisibleItemPosition()) / 2
                if (position > 0) {
                    viewLifecycleOwner.lifecycleScope.launchWhenResumed {
                        if (position != currentPosition) {
                            fragment_cover_flow_grid.stopScroll()
                            scrollToPosition(position)
                        }
                    }
                } else if (position == 0) {
                    scrollToHome()
                }
            }
        }
    }

    fun skipToHome() {
        viewModel.feed.value?.issueMaxDate?.let {
            lifecycleScope.launch { skipToDate(simpleDateFormat.parse(it)!!) }
        }
    }

    private fun skipToPublication(issueKey: IssuePublication) {
        simpleDateFormat.parse(issueKey.date)?.let {
            lifecycleScope.launch { skipToDate(it) }
        }
    }


    override fun onDestroyView() {
        snapHelper.attachToRecyclerView(null)
        fragment_cover_flow_grid.adapter = null
        super.onDestroyView()
    }

    fun setTextAlpha(alpha: Float) {
        fragment_cover_flow_date_download_wrapper.alpha = alpha
    }

    fun getHomeFragment(): HomeFragment = (parentFragment as HomeFragment)
}