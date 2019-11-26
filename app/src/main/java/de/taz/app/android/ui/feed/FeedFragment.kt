package de.taz.app.android.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_feed.*
import kotlinx.coroutines.launch

class FeedFragment : BaseMainFragment<FeedPresenter>() {
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
        feed_archive_pager.reduceDragSensitivity(16)


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

    private fun hideRefreshLoadingIcon() {
        coverflow_refresh_layout.isRefreshing = false
    }
}