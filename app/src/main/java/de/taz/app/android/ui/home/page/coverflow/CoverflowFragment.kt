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
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.setRefreshingWithCallback
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.simpleDateFormat
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.bottomSheet.datePicker.DatePickerFragment
import de.taz.app.android.ui.home.HomeFragment
import de.taz.app.android.ui.home.page.HomePageFragment
import de.taz.app.android.ui.home.page.IssueFeedAdapter
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_coverflow.*
import java.util.*

const val KEY_DATE = "ISSUE_KEY"

class CoverflowFragment : HomePageFragment(R.layout.fragment_coverflow) {

    val log by Log

    override lateinit var adapter: IssueFeedAdapter
    private lateinit var dataService: DataService

    private val snapHelper = GravitySnapHelper(Gravity.CENTER)
    private val onScrollListener = OnScrollListener()

    private var currentDate: Date? = null
        set(value) {
            field = value
            if(value != null) {
                fragment_cover_flow_date?.text = DateHelper.dateToLongLocalizedString(value)
            }
        }

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
            val requestManager = Glide.with(this@CoverflowFragment)
            val fresh = !::adapter.isInitialized
            adapter = CoverflowAdapter(
                this@CoverflowFragment,
                R.layout.fragment_cover_flow_item,
                feed,
                requestManager,
                CoverflowMomentActionListener(this@CoverflowFragment, dataService)
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
        }


        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)

            val layoutManager = recyclerView.layoutManager as LinearLayoutManager
            val position: Int =
                (layoutManager.findFirstVisibleItemPosition() + layoutManager.findLastVisibleItemPosition()) / 2

            applyZoomPageTransformer()

            // persist position and download new issues if user is scrolling
            (parentFragment as? HomeFragment)?.apply {
                if (position == 0) {
                    setHomeIconFilled()
                } else {
                    setHomeIcon()
                }
            }
            if (position >= 0 && !isIdleEvent) {
                setCurrentItem(adapter.getItem(position))
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

}
