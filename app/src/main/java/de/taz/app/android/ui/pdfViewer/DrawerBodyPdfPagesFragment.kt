package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.models.IssueWithPages
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageType
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fragment used in the drawer to display preview of pages with page titles of an issue
 */
class DrawerBodyPdfPagesFragment : Fragment() {

    private val log by Log

    //    TODO(peter) rename prefix: activity -> fragment
    private lateinit var frontPageImageView: ImageView
    private lateinit var frontPageTextView: TextView
    private lateinit var issueDateTextView: TextView
    private lateinit var loadingScreenConstraintLayout: ConstraintLayout
    private lateinit var navigationRecyclerView: RecyclerView

    private lateinit var adapter: PdfDrawerRecyclerViewAdapter

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()

    private lateinit var storageService: StorageService
    private lateinit var tracker: Tracker

    companion object {
        // The drawer initialization will be delayed, so that the main pdf rendering has some time to finish
        private const val DRAWER_INIT_DELAY_MS = 10L
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        storageService = StorageService.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_drawer_body_pdf_pages, container, false)

        frontPageImageView = view.findViewById(R.id.activity_pdf_drawer_front_page)
        frontPageTextView =
            view.findViewById(R.id.activity_pdf_drawer_front_page_title)
        issueDateTextView = view.findViewById(R.id.activity_pdf_drawer_date)
        loadingScreenConstraintLayout = view.findViewById(R.id.pdf_drawer_loading_screen)
        navigationRecyclerView = view.findViewById(R.id.navigation_recycler_view)

        pdfPagerViewModel.pdfPageList.observe(viewLifecycleOwner) {
            initDrawerAdapterWithDelay(it)
        }

        navigationRecyclerView.addOnItemTouchListener(
            RecyclerTouchListener(
                requireContext(),
                fun(_: View, drawerPosition: Int) {
                    tracker.trackDrawerTapPageEvent()
                    log.debug("position clicked: $drawerPosition. pdf")
                    // currentItem.value begins from 0 to n-1th pdf page
                    // but in the drawer the front page is not part of the drawer list, that's why
                    // it needs to be incremented by 1:
                    val realPosition = drawerPosition + 1
                    if (realPosition != pdfPagerViewModel.currentItem.value) {
                        pdfPagerViewModel.updateCurrentItem(realPosition)
                        adapter.activePosition = drawerPosition
                    }
                    (activity as? PdfPagerActivity)?.popArticlePagerFragmentIfOpen()
                    activity?.findViewById<DrawerLayout>(R.id.pdf_drawer_layout)?.closeDrawers()
                }
            )
        )
        return view

    }

    private fun initDrawerAdapterWithDelay(items: List<Page>) {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(DRAWER_INIT_DELAY_MS)
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
                initDrawAdapter(items)
            }
        }
    }

    private fun initDrawAdapter(items: List<Page>) {
        if (items.isNotEmpty()) {
            // Setup a gridManager which takes 2 columns for panorama pages
            val gridLayoutManager = GridLayoutManager(requireContext(), 2)
            gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (items[position + 1].type == PageType.panorama) {
                        2
                    } else {
                        1
                    }
                }
            }

            // Setup Recyclerview's Layout
            navigationRecyclerView.apply {
                layoutManager = gridLayoutManager
                setHasFixedSize(false)
            }

            // Setup drawer header (front page and date)
            Glide
                .with(requireContext())
                .load(storageService.getAbsolutePath(items.first().pagePdf))
                .into(frontPageImageView)

            frontPageImageView.setOnClickListener {
                tracker.trackDrawerTapPageEvent()
                val newPosition = 0
                if (newPosition != pdfPagerViewModel.currentItem.value) {
                    pdfPagerViewModel.updateCurrentItem(newPosition)
                    adapter.activePosition = newPosition
                }
                (activity as? PdfPagerActivity)?.popArticlePagerFragmentIfOpen()
                frontPageTextView.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.pdf_drawer_sections_item_highlighted
                    )
                )
                activity?.findViewById<DrawerLayout>(R.id.pdf_drawer_layout)?.closeDrawers()
            }
            frontPageTextView.apply {
                text = items.first().title
                setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.pdf_drawer_sections_item_highlighted
                    )
                )
            }

            issueDateTextView.text =
                pdfPagerViewModel.issue?.let { setDrawerDate(it) } ?: ""

            adapter =
                PdfDrawerRecyclerViewAdapter(
                    items.subList(1, items.size),
                    Glide.with(requireContext())
                )
            pdfPagerViewModel.currentItem.observe(viewLifecycleOwner) { position ->
                adapter.activePosition = position - 1
                if (position > 0) {
                    log.debug("set front page title color to: ${R.color.pdf_drawer_sections_item}")
                    frontPageTextView.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.pdf_drawer_sections_item
                        )
                    )
                }
            }
            navigationRecyclerView.adapter = adapter
            hideLoadingScreen()
        }

    }

    private fun setDrawerDate(issueWithPages: IssueWithPages): String? {
        val issue = pdfPagerViewModel.issue
        return if (issue?.isWeekend == true && !issue.validityDate.isNullOrBlank()) {
            DateHelper.stringsToWeek2LineString(
                issue.date,
                issue.validityDate
            )
        } else {
            DateHelper.stringToLongLocalized2LineString(issueWithPages.date)
        }
    }

    private fun hideLoadingScreen() {
        activity?.runOnUiThread {
            loadingScreenConstraintLayout.apply {
                animate()
                    .alpha(0f)
                    .withEndAction {
                        this.visibility = View.GONE
                    }
                    .duration = LOADING_SCREEN_FADE_OUT_TIME
            }
        }
    }

}