package de.taz.app.android.ui.home.page.coverflow


import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.github.rubensousa.gravitysnaphelper.GravitySnapHelper
import de.taz.app.android.R
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.CoverFlowDatePosition
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
    private lateinit var dataService: DataService

    private val authHelper by lazy { AuthHelper.getInstance(context) }
    private val toastHelper by lazy { ToastHelper.getInstance(context) }

    private val snapHelper = GravitySnapHelper(Gravity.CENTER)
    private val onScrollListener = OnScrollListener()

    private var downloadObserver: DownloadObserver? = null
    private var initialIssueDisplay: IssueKey? = null


    override fun onAttach(context: Context) {
        super.onAttach(context)
        dataService = DataService.getInstance(requireContext().applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If this is mounted on MainActivity with ISSUE_KEY extra skip to that issue on creation
        initialIssueDisplay =
            requireActivity().intent.getParcelableExtra(MainActivity.KEY_ISSUE_KEY)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.currentDate.observe(viewLifecycleOwner) {
            downloadObserver?.unbindView()
            downloadObserver = DownloadObserver(
                this,
                dataService,
                authHelper,
                toastHelper,
                it.date,
                viewModel.feed.value!!
            ).apply {
                bindView(fragment_cover_flow_date_download_wrapper)
            }
            fragment_cover_flow_date?.text = DateHelper.dateToLongLocalizedString(it.date)
            if (it.scrollTo) { skipToDate(it.date) }
        }
        fragment_cover_flow_grid.apply {
            layoutManager = object : LinearLayoutManager(requireContext(), HORIZONTAL, false) {
                /**
                 * We need to create a padding allow this recyclerview to snap also the first and the last item
                 * in the middle of the screen. Without it the first item would stick to the left side of the screen
                 */
                override fun getPaddingLeft(): Int {
                    return fragment_cover_flow_grid.children.firstOrNull()?.let {
                        if (it.measuredWidth > 0) {
                            return fragment_cover_flow_grid.width / 2 - it.measuredWidth / 2
                        } else 0
                    } ?: 0
                }

                override fun getPaddingRight(): Int {
                    return paddingLeft
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
            openDatePicker()
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
        super.onResume()
    }

    private fun openDatePicker() {
        showBottomSheet(DatePickerFragment())
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
        fragment_cover_flow_grid.layoutManager?.scrollToPosition(0)
        snapHelper.scrollToPosition(0)
        (parentFragment as? HomeFragment)?.setHomeIconFilled()
    }

    private fun skipToDate(date: Date?) {
        if (!::adapter.isInitialized) {
            return
        }
        date?.let {
            val position = adapter.getPosition(it)
            val layoutManager = fragment_cover_flow_grid.layoutManager as? LinearLayoutManager
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
                    skipToHome()
                }
            }
        } ?: skipToHome()
    }

    fun skipToKey(issueKey: IssueKey) {
        simpleDateFormat.parse(issueKey.date)?.let {
            skipToDate(it)
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
            val position: Int = snapHelper.currentSnappedPosition
            applyZoomPageTransformer()
            (parentFragment as? HomeFragment)?.apply {
                if (position == 0) {
                    setHomeIconFilled()
                } else {
                    setHomeIcon()
                }
            }

            // set position
            if (position >= 0 && !isIdleEvent) {
                adapter.getItem(position)?.let { date ->
                    viewModel.currentDate.postValue(
                        CoverFlowDatePosition(date, scrollTo = false)
                    )
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
