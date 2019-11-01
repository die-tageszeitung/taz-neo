package de.taz.app.android.ui.archive

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.taz.app.android.R
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.main.MainContract
import kotlinx.android.synthetic.main.fragment_archive.*

class ArchiveFragment : BaseFragment<ArchiveContract.Presenter>(), ArchiveContract.View {

    override val presenter = ArchivePresenter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_archive, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_archive_swipe_refresh.setOnRefreshListener {
            presenter.onRefresh()
        }

        context?.let { context ->
            fragment_archive_grid.adapter = ArchiveListAdapter(this)
        }

        presenter.attach(this)
        presenter.onViewCreated()
    }

    override fun onDataSetChanged(issueStubs: List<IssueStub>) {
        (fragment_archive_grid?.adapter as? ArchiveListAdapter)?.updateMomentList(issueStubs)
    }

    override fun hideScrollView() {
        fragment_archive_swipe_refresh.isRefreshing = false
    }

    override fun getMainView(): MainContract.View? {
        return activity as? MainActivity
    }

}