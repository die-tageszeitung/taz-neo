package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.StableIdProvider
import de.taz.app.android.util.StableIdViewModel
import kotlinx.android.synthetic.main.fragment_webview_pager.*

class SectionPagerFragment : BaseMainFragment<SectionPagerPresenter>(),
    SectionPagerContract.View,
    BackFragment {

    override val presenter = SectionPagerPresenter()

    private var initialSection: Section? = null

    companion object {
        fun createInstance(initialSection: Section): SectionPagerFragment {
            // FIXME: think about using the Bundle with a  id and getting the data from the viewmodel directly
            val fragment = SectionPagerFragment()
            fragment.initialSection = initialSection
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Attach the presenter to this view and ensure its datamodel is created and bound to this fragments lifecycle
        presenter.attach(this)

        // Ensure initial fragment states are copied to the model via the presenter
        initialSection?.let { presenter.setInitialSection(it) }

        // Initialize the presenter and let it call this fragment to render the pager
        presenter.onViewCreated(savedInstanceState)

        setupViewPager()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webview_pager_viewpager.adapter = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview_pager, container, false)
    }

    // FIXME: would love to register on main instead of implementing this via typechecking
    // This would also allow us to stack back handlers: for example while drawer is open its back handler is active,
    // when it is unregistered the previous callback handler will become active again.
    override fun onBackPressed(): Boolean {
        presenter.onBackPressed()
        return true
    }

    private fun setupViewPager() {
        val stableIdProvider = ViewModelProviders.of(this).get(StableIdViewModel::class.java)
        val sectionAdapter = SectionPagerAdapter(this, stableIdProvider)
        webview_pager_viewpager.apply {
            adapter = sectionAdapter
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 1
            registerOnPageChangeCallback(pageChangeListener)
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            this@SectionPagerFragment.presenter.setCurrentPosition(position)
        }
    }

    override fun setSections(sections: List<Section>, currentPosition: Int) {
        webview_pager_viewpager.apply {
            (adapter as SectionPagerAdapter?)?.submitList(sections)
            setCurrentItem(currentPosition, false)
        }
    }

    private class SectionPagerAdapter(
        fragment: Fragment,
        private val stableIdProvider: StableIdProvider
    ) : FragmentStateAdapter(fragment) {
        private var sections = emptyList<Section>()
        val log by Log

        override fun createFragment(position: Int): Fragment {
            val id = getItemId(position)
            val section = sections[position]
            log.debug("create fragment: pos=$position id=$id section=${section.sectionFileName}")
            return SectionWebViewFragment.createInstance(section)
        }

        override fun getItemCount(): Int = sections.size

        fun submitList(newSections: List<Section>) {
            sections = newSections
            notifyDataSetChanged()
        }

//        fun submitList(newSections: List<Section>) {
//            if (sections.size == 1) {
//                val position =
//                    newSections.indexOfFirst { section -> section.sectionFileName == sections[0].sectionFileName }
//                submitListContainingCurrent(newSections, position)
//            } else {
//                sections = newSections
//                notifyDataSetChanged()
//            }
//        }
//
//        private fun submitListContainingCurrent(newSections: List<Section>, position: Int) {
//            sections = newSections
//            if (position >= 0) {
//                if (position > 0) {
//                    notifyItemRangeInserted(0, position)
//                }
//                if (position < sections.size - 1) {
//                    val positionStart = position + 1
//                    val itemCount = sections.size - positionStart
//                    notifyItemRangeInserted(positionStart, itemCount)
//                }
//            } else {
//                notifyDataSetChanged()
//            }
//        }

        override fun getItemId(position: Int): Long {
            val filename = sections[position].sectionFileName
            return stableIdProvider.getId(filename)
        }
    }
}