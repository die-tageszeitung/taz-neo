package de.taz.app.android.ui.archive.endNavigation;

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.base.BaseFragment
import kotlinx.android.synthetic.main.fragment_archive_end_navigation.*


class ArchiveEndNavigationFragment : BaseFragment<ArchiveEndNavigationContract.Presenter>(),
    ArchiveEndNavigationContract.View {

    override val presenter = ArchiveEndNavigationPresenter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_archive_end_navigation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.attach(this)
        presenter.onViewCreated()
    }

    override fun setFeeds(feeds: List<Feed>) {
        context?.let { context ->
            fragment_archive_navigation_end_feed_list.adapter = ArrayAdapter<String>(
                context, android.R.layout.simple_list_item_1, feeds.map { it.name }
            )
        }
    }

}
