package de.taz.app.android.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.taz.app.android.R
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_feed.*

class FeedFragment : BaseMainFragment<FeedPresenter>() {
    val log by Log

    override val presenter: FeedPresenter = FeedPresenter()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        presenter.attach(this)
        feed_archive_pager.adapter = FeedFragmentPagerAdapter(childFragmentManager)
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        presenter.onBottomNavigationItemClicked(menuItem, activated)
    }
}