package de.taz.app.android.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_feed.*
import kotlinx.coroutines.launch

class FeedFragment : BaseMainFragment<FeedPresenter>(), FeedContract.View {
    val log by Log

    override val presenter: FeedPresenter = FeedPresenter()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter.attach(this)
        feed_archive_pager.adapter = FeedFragmentPagerAdapter(childFragmentManager, lifecycle)

        // reduce viewpager2 sensitivity to make the view less finnicky
        feed_archive_pager.reduceDragSensitivity(12)
        feed_archive_pager.registerOnPageChangeCallback(object: ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                when (position) {
                    COVERFLOW_PAGER_POSITION -> enableRefresh()
                    ARCHIVE_PAGER_POSITION -> disableRefresh()
                }
            }
        })

        coverflow_refresh_layout.setOnRefreshListener {
            lifecycleScope.launch {
                presenter.onRefresh()
                hideRefreshLoadingIcon()
            }
        }
        coverflow_refresh_layout.reduceDragSensitivity(10)

    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        presenter.onBottomNavigationItemClicked(menuItem, activated)
    }

    override fun enableRefresh() {
        coverflow_refresh_layout.isEnabled = true
    }

    override fun disableRefresh() {
        coverflow_refresh_layout.isEnabled = false
    }

    private fun hideRefreshLoadingIcon() {
        coverflow_refresh_layout.isRefreshing = false
    }
}