package de.taz.app.android.ui.home.page.coverflow


import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper
import de.taz.app.android.R
import de.taz.app.android.content.ContentService
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import de.taz.app.android.ui.home.page.IssueFeedFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_coverflow.*
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.abs

class CoverflowFragment : IssueFeedFragment(R.layout.fragment_coverflow) {

    val log by Log

    override lateinit var adapter: IssueFeedAdapter
    private lateinit var dataService: DataService

    private val toastHelper by lazy { ToastHelper.getInstance(requireContext().applicationContext) }
    private val contentService by lazy { ContentService.getInstance(requireContext().applicationContext) }

    private val snapHelper = GravitySnapHelper(Gravity.CENTER)
    private val onScrollListener = OnScrollListener()

    private lateinit var orientationHelper: OrientationHelper

    private var downloadObserver: DownloadObserver? = null
    private var initialIssueDisplay: IssuePublication? = null

    private var currentlyBoundPosition: Int? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dataService = DataService.getInstance(requireContext().applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If this is mounted on MainActivity with ISSUE_KEY extra skip to that issue on creation
        initialIssueDisplay =
            requireActivity().intent.getParcelableExtra(MainActivity.KEY_ISSUE_PUBLICATION)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_cover_flow_grid.apply {
            layoutManager = CoverFlowLinearLayoutManager(requireContext(), fragment_cover_flow_grid)
            orientationHelper = OrientationHelper.createHorizontalHelper(layoutManager)

            // transform the visible children visually
            snapHelper.apply {
                attachToRecyclerView(fragment_cover_flow_grid)
                maxFlingSizeFraction = 0.75f
                snapLastItem = true
            }


            addOnChildAttachStateChangeListener(object :
                RecyclerView.OnChildAttachStateChangeListener {

                override fun onChildViewAttachedToWindow(view: View) {
                    applyZoomPageTransformer()
                }

                override fun onChildViewDetachedFromWindow(view: View) = Unit
            })
        }

        fragment_cover_flow_to_archive.setOnClickListener {
            (parentFragment as?  HomeFragment)?.showArchive()
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
        viewModel.currentDate.observeDistinct(viewLifecycleOwner) {
            lifecycleScope.launch {
                skipToDate(it)
            }
        }
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

    private fun skipToDate(date: Date, scroll: Boolean = true) {
        if (!::adapter.isInitialized) {
            return
        }
        downloadObserver?.stopObserving()

        downloadObserver = DownloadObserver(
            this,
            contentService,
            toastHelper,
            IssuePublication(viewModel.feed.value!!.name, simpleDateFormat.format(date)),
            fragment_coverflow_moment_download,
            fragment_coverflow_moment_download_finished,
            fragment_coverflow_moment_downloading
        ).apply {
            startObserving()
        }
        fragment_cover_flow_date?.text = DateHelper.dateToLongLocalizedString(date)
        currentlyBoundPosition = adapter.getPosition(date)
        currentlyBoundPosition?.let { position ->
            val layoutManager = fragment_cover_flow_grid.layoutManager as? LinearLayoutManager

            if (scroll) {
                layoutManager?.apply {
                    val currentPosition: Int =
                        (findFirstVisibleItemPosition() + findLastVisibleItemPosition()) / 2
                    if (position > 0) {
                        viewLifecycleOwner.lifecycleScope.launchWhenResumed {
                            if (position != currentPosition) {
                                fragment_cover_flow_grid.stopScroll()
                                layoutManager.scrollToPosition(position)
                            }
                        }
                    } else if (position == 0) {
                        scrollToHome()
                    }
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

    inner class OnScrollListener : RecyclerView.OnScrollListener() {

        private var isDragEvent = false
        private var isIdleEvent = false
        private var isSettlingEvent = false

        override fun onScrollStateChanged(
            recyclerView: RecyclerView,
            newState: Int
        ) {
            super.onScrollStateChanged(recyclerView, newState)

            // if user is dragging to left if no newer issue -> refresh
            if (isDragEvent && !recyclerView.canScrollHorizontally(-1)) {
                activity?.findViewById<SwipeRefreshLayout>(R.id.coverflow_refresh_layout)
                    ?.setRefreshingWithCallback(true)
            }
            isDragEvent = newState == RecyclerView.SCROLL_STATE_DRAGGING
            isIdleEvent = newState == RecyclerView.SCROLL_STATE_IDLE
            isSettlingEvent = newState == RecyclerView.SCROLL_STATE_SETTLING

        }

        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            val layoutManager = recyclerView.layoutManager as LinearLayoutManager

            snapHelper.findSnapView(layoutManager)?.let {
                val currentViewDistance = abs(
                        orientationHelper.startAfterPadding
                                - orientationHelper.getDecoratedStart(it)
                    )
                val alphaPercentage = 1 -
                    (currentViewDistance.toFloat() * 2 / orientationHelper.totalSpace)

                fragment_cover_flow_date_download_wrapper.alpha = alphaPercentage

                val position = recyclerView.getChildAdapterPosition(it)
                setSelectedDateByPosition(position)
            }
            applyZoomPageTransformer()
        }

        private fun setSelectedDateByPosition(position: Int) {
            (parentFragment as? HomeFragment)?.apply {
                if (position == 0) {
                    setHomeIconFilled()
                } else {
                    setHomeIcon()
                }
            }
            adapter.getItem(position)?.let { date ->
                if (position != currentlyBoundPosition) {
                    lifecycleScope.launch {
                        skipToDate(date, scroll = false)
                    }
                }
            }
        }
    }

    private fun applyZoomPageTransformer() {
        (fragment_cover_flow_grid as? ViewGroup)?.apply {
            children.forEach { child ->
                val childPosition = (child.left + child.right) / 2f
                val center = width / 2
                if (childPosition != 0f) {
                    ZoomPageTransformer.transformPage(child, (center - childPosition) / width)
                }
            }
        }
    }

    override fun callbackWhenIssueIsSet() {
        applyZoomPageTransformer()
    }

    override fun onDestroyView() {
        snapHelper.attachToRecyclerView(null)
        fragment_cover_flow_grid.adapter = null
        super.onDestroyView()
    }

}
