package de.taz.app.android.ui.home.page

import androidx.lifecycle.Observer
import de.taz.app.android.api.models.IssueStub

/**
 * HomePageIssuesObserver is used by [HomePagePresenter]
 * to observe [HomePageDataController]'s issueStubList and bitmaps.
 * It updates [HomePageAdapter]
 */
class HomePageIssueStubsObserver(
    private val homePagePresenter: HomePagePresenter
) : Observer<List<IssueStub>> {

    override fun onChanged(issueStubs: List<IssueStub>?) {
        homePagePresenter.apply {
            onDataSetChanged(issueStubs ?: emptyList())
        }
    }

}
