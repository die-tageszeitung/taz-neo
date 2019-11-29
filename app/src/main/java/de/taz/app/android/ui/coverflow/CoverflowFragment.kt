package de.taz.app.android.ui.coverflow


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.main.MainContract
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_coverflow.*


class CoverflowFragment: Fragment(), CoverflowContract.View {
    val presenter = CoverflowPresenter()

    val log by Log

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_coverflow, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)
    }

    override fun onDatasetChanged(issues: List<IssueStub>, feed: Feed?) {
        coverflow_pager.adapter = CoverflowPagerAdapter(requireContext(), viewLifecycleOwner, issues, feed)
    }

    override fun getLifecycleOwner(): LifecycleOwner {
        return viewLifecycleOwner
    }

    override fun getMainView(): MainContract.View? {
        return activity as MainActivity
    }

    override fun skipToEnd() {
        coverflow_pager.adapter?.let {
            coverflow_pager.currentItem = it.count - 1
        }
    }
}