package de.taz.app.android.ui.home.page

import androidx.lifecycle.Observer
import de.taz.app.android.api.models.IssueStub

/**
 * HomePageIssuesObserver is used by [HomePageFragment]
 * to observe [HomePageDataController]'s issueStubList and bitmaps.
 * It updates [HomePageAdapter]
 */
class HomePageIssueStubsObserver(
    private val homePageFragment: HomePageFragment
) : Observer<List<IssueStub>> {

    override fun onChanged(issueStubs: List<IssueStub>?) {
        homePageFragment.apply {
            onDataSetChanged(issueStubs ?: emptyList())
        }
    }

}
