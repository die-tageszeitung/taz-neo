package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import de.taz.app.android.R
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_webview_pager.*

class SectionPagerFragment : BaseMainFragment<SectionPagerPresenter>(),
    SectionPagerContract.View,
    BackFragment {

    override val presenter = SectionPagerPresenter()

    val log by Log

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
        log.debug("onViewCreated: $view $savedInstanceState")

        // Attach the presenter to this view and ensure its datamodel is created and bound to this fragments lifecycle
        presenter.attach(this)

        // Ensure initial fragment states are copied to the model via the presenter
        initialSection?.let { presenter.setInitialSection(it) }

        // Initialize the presenter and let it call this fragment to render the pager
        presenter.onViewCreated(savedInstanceState)
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

    override fun setSections(sections: List<Section>, currentPosition: Int) {
        webview_pager_viewpager.apply {
            adapter = SectionPagerAdapter(
                sections,
                childFragmentManager
            )
            offscreenPageLimit = 1
            currentItem = currentPosition
            addOnPageChangeListener(pageChangeListener)
        }
        hideLoadingScreen()
    }

    private val pageChangeListener = object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            this@SectionPagerFragment.presenter.setCurrrentPosition(position)
        }
    }

    private class SectionPagerAdapter(
        private val sections: List<Section>,
        fragmentManager: FragmentManager
    ) : FragmentStatePagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        val log by Log

        override fun getItem(position: Int): Fragment {
            val section = sections[position]
            log.debug("getItem($position) = ${section.sectionFileName}")
            return SectionWebViewFragment.createInstance(section)
        }

        override fun getCount(): Int = sections.size

        // Do not save the state between orientation changes. This will be handled by the presenter
        // which will instruct to create a new adapter altogether
        override fun saveState(): Parcelable? = null
    }

    private fun hideLoadingScreen() {
        activity?.runOnUiThread {
            if (webview_pager_spinner.visibility == View.VISIBLE) {
                webview_pager_spinner.visibility = View.GONE
                webview_pager_viewpager.visibility = View.VISIBLE
            }
        }
    }
}