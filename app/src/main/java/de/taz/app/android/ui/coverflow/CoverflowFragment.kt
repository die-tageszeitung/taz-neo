package de.taz.app.android.ui.coverflow


import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.main.MainContract
import de.taz.app.android.ui.main.MainDataController
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_coverflow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class CoverflowFragment : Fragment(), CoverflowContract.View {
    val presenter = CoverflowPresenter()

    val log by Log

    private val issueRepository = IssueRepository.getInstance()

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

        coverflow_pager.addOnPageChangeListener(object : CoverFlowOnPageChangeListener() {
            override suspend fun onIssueStubSelected(issue: Issue) {
                val viewModel = ViewModelProviders.of(requireActivity()).get(
                    MainDataController::class.java
                )
                viewModel.setIssue(issue)
            }
        })
    }

    override fun onDatasetChanged(issues: List<IssueStub>, feed: Feed?) {
        coverflow_pager.adapter = CoverflowPagerAdapter(
            requireContext(), viewLifecycleOwner, issues, feed
        )
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

    abstract inner class CoverFlowOnPageChangeListener : ViewPager.OnPageChangeListener {
        override fun onPageScrollStateChanged(state: Int) = Unit

        override fun onPageScrolled(
            position: Int,
            positionOffset: Float,
            positionOffsetPixels: Int
        ) = Unit

        override fun onPageSelected(position: Int) {
            (coverflow_pager.adapter as? CoverflowPagerAdapter)?.let { adapter ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    onIssueStubSelected(
                        issueRepository.getIssue(
                            adapter.getIssueAtPosition(position)
                        )
                    )
                }
            }
        }

        abstract suspend fun onIssueStubSelected(issue: Issue)
    }
}
