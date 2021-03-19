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
import de.taz.app.android.api.models.DownloadStatus
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.ui.cover.MOMENT_FADE_DURATION_MS
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.IssueFeedFragment
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.android.synthetic.main.fragment_coverflow.*
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

const val KEY_DATE = "KEY_DATE"

class CoverflowFragment: IssueFeedFragment(R.layout.fragment_coverflow) {

    val log by Log

    override lateinit var adapter: IssueFeedAdapter
    private lateinit var dataService: DataService

    private val authHelper by lazy { AuthHelper.getInstance(context) }
    private val toastHelper by lazy { ToastHelper.getInstance(context) }

    private val snapHelper = GravitySnapHelper(Gravity.CENTER)
    private val onScrollListener = OnScrollListener()

    private var downloadObserverJob: Job? = null
    private var currentDate: Date? = null
        set(value) {
            if (field != value) {
                field = value
                if (value != null) {
                    fragment_cover_flow_date?.text = DateHelper.dateToLongLocalizedString(value)
                }
            }
        }

    private var initialIssueDisplay: IssueKey? = null



    override fun onAttach(context: Context) {
        super.onAttach(context)
        dataService = DataService.getInstance(requireContext().applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            currentDate = savedInstanceState.getSerializable(KEY_DATE) as Date?
        }
        // If this is mounted on MainActivity with ISSUE_KEY extra skip to that issue on creation
        initialIssueDisplay =
            requireActivity().intent.getParcelableExtra(MainActivity.KEY_ISSUE_KEY)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragment_cover_flow_grid.apply {
            layoutManager = object : LinearLayoutManager(requireContext(), HORIZONTAL, false) {

                override fun calculateExtraLayoutSpace(
                    state: RecyclerView.State,
                    extraLayoutSpace: IntArray
                ) {
                    super.calculateExtraLayoutSpace(state, extraLayoutSpace)
                    val orientationHelper =
                        OrientationHelper.createOrientationHelper(this, orientation)
                    val extraSpace = orientationHelper.totalSpace
                    extraLayoutSpace[0] = extraSpace
                    extraLayoutSpace[1] = extraSpace
                }
            }

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
            activity?.findViewById<ViewPager2>(R.id.feed_archive_pager)?.apply {
                currentItem += 1
            }
        }

        fragment_cover_flow_date.setOnClickListener {
            currentDate?.let { openDatePicker(it) }
        }

        viewModel.feed.observeDistinct(this) { feed ->
            val fresh = !::adapter.isInitialized
            val requestManager = Glide.with(this)
            val itemLayout = if (viewModel.pdfMode.value == true) {
                R.layout.fragment_cover_flow_frontpage_item
            } else {
                R.layout.fragment_cover_flow_moment_item
            }
            adapter = CoverflowAdapter(
                this,
                itemLayout,
                feed,
                requestManager,
                CoverflowCoverViewActionListener(this@CoverflowFragment, dataService)
            )
            fragment_cover_flow_grid.adapter = adapter
            // If fragment was just constructed skip to issue in intent
            if (fresh && savedInstanceState == null) {
                initialIssueDisplay?.let { skipToKey(it) }
            }
        }
    }

    override fun onResume() {
        fragment_cover_flow_grid.addOnScrollListener(onScrollListener)
        skipToCurrentItem()
        super.onResume()
    }

    fun openDatePicker(issueDate: Date) {
        showBottomSheet(DatePickerFragment.create(this, viewModel.feed.value!!, issueDate))
    }

    override fun onPause() {
        fragment_cover_flow_grid.removeOnScrollListener(onScrollListener)
        super.onPause()
    }


    fun skipToHome() {
        if (!::adapter.isInitialized) {
            return
        }
        fragment_cover_flow_grid.stopScroll()
        setCurrentItem(adapter.getItem(0))
        fragment_cover_flow_grid.layoutManager?.scrollToPosition(0)
        snapHelper.scrollToPosition(0)
        (parentFragment as? HomeFragment)?.setHomeIconFilled()
    }

    private fun skipToCurrentItem() {
        if (!::adapter.isInitialized) {
            return
        }
        currentDate?.let {
            val position = adapter.getPosition(it)
            val layoutManager = fragment_cover_flow_grid.layoutManager as? LinearLayoutManager
            layoutManager?.apply {
                val currentPosition: Int =
                    (findFirstVisibleItemPosition() + findLastVisibleItemPosition()) / 2
                if (position > 0) {
                    viewLifecycleOwner.lifecycleScope.launchWhenResumed {
                        if (position != currentPosition) {
                            fragment_cover_flow_grid.stopScroll()
                            setCurrentItem(adapter.getItem(position))
                            layoutManager.scrollToPosition(position)
                        }
                    }
                } else if (position == 0) {
                    skipToHome()
                }
            }
        } ?: skipToHome()
    }

    fun skipToKey(issueKey: IssueKey) {
        currentDate = simpleDateFormat.parse(issueKey.date)
        skipToCurrentItem()
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

            // TODO find widescreen option
            val position: Int = layoutManager.findLastCompletelyVisibleItemPosition()

            applyZoomPageTransformer()

            (parentFragment as? HomeFragment)?.apply {
                if (position == 0) {
                    setHomeIconFilled()
                } else {
                    setHomeIcon()
                }
            }

            // set position and show date and observer when settled
            if (position >= 0 && !isIdleEvent) {
                setCurrentItem(adapter.getItem(position))

                if (isSettlingEvent && dx < 5) {
                    currentDate?.let {
                        fragment_cover_flow_date?.visibility = View.VISIBLE
                        downloadObserverJob?.cancel()
                        downloadObserverJob = startDownloadObserver()
                    }
                }
            }

            // hide date and download indicator if dragging
            if (isDragEvent) {
                fragment_cover_flow_date?.visibility = View.GONE
                downloadObserverJob?.cancel()
                hideDownloadIcon(true)
            }

            // if first start start downloadObserver
            if(!isIdleEvent && !isDragEvent && !isSettlingEvent) {
                log.error("starting first time")
                downloadObserverJob = startDownloadObserver()
            }
        }
    }

    private fun setCurrentItem(date: Date?) {
        currentDate = date
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putSerializable(KEY_DATE, currentDate)
        super.onSaveInstanceState(outState)
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


    private fun startDownloadObserver(): Job? {
        return runIfNotNull(currentDate, viewModel.feed.value) { currentDate, feed ->
            return@runIfNotNull lifecycleScope.launch(Dispatchers.IO) {
                log.error("observe lauinched")
                dataService.withDownloadLiveData(
                    IssueKey(
                        feed.name,
                        simpleDateFormat.format(currentDate),
                        authHelper.eligibleIssueStatus
                    )
                ) {
                    withContext(Dispatchers.Main) {
                        it.observeDistinct(this@CoverflowFragment) { downloadStatus ->
                            setDownloadIconForStatus(
                                downloadStatus
                            )
                        }
                    }
                }
            }
        }
    }

    private fun setDownloadIconForStatus(downloadStatus: DownloadStatus) {
        when (downloadStatus) {
            DownloadStatus.done -> hideDownloadIcon()
            DownloadStatus.started -> showLoadingIcon()
            else -> showDownloadIcon()
        }
    }

    private fun showDownloadIcon() {
        var noConnectionShown = false
        fun onConnectionFailure() {
            if (!noConnectionShown) {
                lifecycleScope.launch {
                    toastHelper.showNoConnectionToast()
                    noConnectionShown = true
                }
            }
        }
        fragment_coverflow_moment_download?.setOnClickListener {
            runIfNotNull(currentDate, viewModel.feed.value) { currentDate, feed ->
                CoroutineScope(Dispatchers.IO).launch {
                    log.error("download launched")
                    // we refresh the issue from network, as the cache might be pretty stale at this point (issues might be edited after release)
                    val issue = dataService.getIssue(
                        IssuePublication(feed.name, simpleDateFormat.format(currentDate)),
                        retryOnFailure = true,
                        allowCache = false,
                        onConnectionFailure = { onConnectionFailure() }
                    )
                    dataService.ensureDownloaded(
                        collection = issue,
                        onConnectionFailure = { onConnectionFailure() }
                    )
                }
                showLoadingIcon()
            }
        }
        fragment_coverflow_moment_downloading?.visibility = View.GONE
        fragment_coverflow_moment_download_finished?.visibility = View.GONE
        fragment_coverflow_moment_download?.visibility = View.VISIBLE
    }

    // TODO better find out why done is triggered multiple times instead of using this workaround
    private val isHiding = AtomicBoolean(false)
    private fun hideDownloadIcon(reset: Boolean = false) {
        if(isHiding.getAndSet(true)) {
            log.error("hideDownloadIcon triggered")
            fragment_coverflow_moment_download.setOnClickListener(null)
            val wasDownloading = fragment_coverflow_moment_downloading?.visibility == View.VISIBLE
            fragment_coverflow_moment_downloading?.visibility = View.GONE
            fragment_coverflow_moment_download?.visibility = View.GONE
            fragment_coverflow_moment_download_finished?.visibility = View.GONE

            if (wasDownloading && !reset) {
                fragment_coverflow_moment_download_finished?.apply {
                    alpha = 1f
                    visibility = View.VISIBLE
                    animate().alpha(0f).apply {
                        duration = MOMENT_FADE_DURATION_MS
                        startDelay = 2000L
                    }.withEndAction {
                        isHiding.set(false)
                    }
                }
            }
        }
    }

    private fun showLoadingIcon() {
        fragment_coverflow_moment_download.setOnClickListener(null)
        fragment_coverflow_moment_download?.visibility = View.GONE
        fragment_coverflow_moment_download_finished?.visibility = View.GONE
        fragment_coverflow_moment_downloading?.visibility = View.VISIBLE
    }

}
