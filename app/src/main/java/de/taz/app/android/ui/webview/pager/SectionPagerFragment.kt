package de.taz.app.android.ui.webview.pager

import android.content.Context
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
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.StableIdProvider
import de.taz.app.android.util.StableIdViewModel
import kotlinx.android.synthetic.main.fragment_webview_pager.*

class SectionPagerFragment : BaseMainFragment<SectionPagerPresenter>(),
    SectionPagerContract.View,
    BackFragment {

    override val presenter = SectionPagerPresenter()

    private var initialSection: Section? = null

    private var stableIdProvider: StableIdViewModel? = null
    private var sectionAdapter: SectionPagerAdapter? = null

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
        webview_pager_viewpager.reduceDragSensitivity()

        stableIdProvider = ViewModelProviders.of(this).get(StableIdViewModel::class.java).also {
            sectionAdapter = SectionPagerAdapter(this, it)
        }

        setupViewPager()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webview_pager_viewpager?.adapter = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview_pager, container, false)
    }

    override fun onBackPressed(): Boolean {
        getCurrentFragment()?.let {
            if (it.onBackPressed()) return true
        }
        presenter.onBackPressed()
        return true
    }

    private fun getCurrentFragment(): SectionWebViewFragment? {
        return childFragmentManager.fragments.firstOrNull {
            (it as? SectionWebViewFragment)?.let { fragment ->
                return@firstOrNull fragment.section == sectionAdapter?.getCurrentSection()
            }
            return@firstOrNull false
        } as? SectionWebViewFragment
    }

    override fun tryLoadSection(section: Section): Boolean {
        return presenter.trySetSection(section)
    }

    private fun setupViewPager() {
        webview_pager_viewpager?.apply {
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

    override fun setCurrentPosition(currentPosition: Int) {
        webview_pager_viewpager.setCurrentItem(currentPosition, false)
    }

    private inner class SectionPagerAdapter(
        fragment: Fragment,
        private val stableIdProvider: StableIdProvider
    ) : FragmentStateAdapter(fragment) {
        private var sections = emptyList<Section>()

        override fun createFragment(position: Int): Fragment {
            val section = sections[position]
            return SectionWebViewFragment.createInstance(section)
        }

        override fun getItemCount(): Int = sections.size

        fun submitList(newSections: List<Section>) {
            sections = newSections
            notifyDataSetChanged()
        }

        override fun getItemId(position: Int): Long {
            val filename = sections[position].sectionFileName
            return stableIdProvider.getId(filename)
        }

        fun getCurrentSection(): Section {
            return sections[webview_pager_viewpager.currentItem]
        }
    }
}