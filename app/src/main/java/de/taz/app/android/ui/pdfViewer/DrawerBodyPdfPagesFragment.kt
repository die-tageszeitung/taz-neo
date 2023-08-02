package de.taz.app.android.ui.pdfViewer

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.models.IssueWithPages
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageType
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.databinding.FragmentDrawerBodyPdfPagesBinding
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.util.Log

/**
 * Fragment used in the drawer to display preview of pages with page titles of an issue
 */
class DrawerBodyPdfPagesFragment : ViewBindingFragment<FragmentDrawerBodyPdfPagesBinding>() {

    private val log by Log

    private lateinit var adapter: PdfDrawerRecyclerViewAdapter

    private val pdfPagerViewModel: PdfPagerViewModel by activityViewModels()
    private val drawerAndLogoViewModel: DrawerAndLogoViewModel by activityViewModels()

    private lateinit var storageService: StorageService
    private lateinit var tracker: Tracker

    override fun onAttach(context: Context) {
        super.onAttach(context)
        storageService = StorageService.getInstance(context.applicationContext)
        tracker = Tracker.getInstance(context.applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Disable animations on the RV in hope of preventing some internal crashes:
        // See: https://redmine.hal.taz.de/issues/15694
        // Note: The RV should actually not recycle anything because it has effectively set its
        // height to `wrap_content` (it has `match_parent` but the parent itself has `wrap_content`)
        // and we are not doing any structural changes. As every crash was related to some animations
        // it seems like a good try to disable the not-needed animations at all.
        viewBinding.navigationRecyclerView.itemAnimator = null

        pdfPagerViewModel.pdfPageList.observe(viewLifecycleOwner) {
            initDrawAdapter(it)
        }

        viewBinding.navigationRecyclerView.addOnItemTouchListener(
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
                    drawerAndLogoViewModel.closeDrawer()
                }
            )
        )
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
            viewBinding.navigationRecyclerView.apply {
                layoutManager = gridLayoutManager
                setHasFixedSize(false)
            }

            // Setup drawer header (front page and date)
            Glide
                .with(requireContext())
                .load(storageService.getAbsolutePath(items.first().pagePdf))
                .into(viewBinding.activityPdfDrawerFrontPage)

            viewBinding.activityPdfDrawerFrontPage.setOnClickListener {
                tracker.trackDrawerTapPageEvent()
                val newPosition = 0
                if (newPosition != pdfPagerViewModel.currentItem.value) {
                    pdfPagerViewModel.updateCurrentItem(newPosition)
                    adapter.activePosition = newPosition
                }
                (activity as? PdfPagerActivity)?.popArticlePagerFragmentIfOpen()
                viewBinding.activityPdfDrawerFrontPageTitle.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.pdf_drawer_sections_item_highlighted
                    )
                )
                drawerAndLogoViewModel.closeDrawer()
            }
            viewBinding.activityPdfDrawerFrontPageTitle.apply {
                text = items.first().title
                setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        R.color.pdf_drawer_sections_item_highlighted
                    )
                )
            }

            viewBinding.activityPdfDrawerDate.text =
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
                    viewBinding.activityPdfDrawerFrontPageTitle.setTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.pdf_drawer_sections_item
                        )
                    )
                }
            }
            viewBinding.navigationRecyclerView.adapter = adapter
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
            viewBinding.pdfDrawerLoadingScreen.root.apply {
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