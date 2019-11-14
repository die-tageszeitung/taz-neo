package de.taz.app.android.ui.archive.main

import androidx.lifecycle.Observer
import de.taz.app.android.api.models.IssueStub

/**
 * ArchiveIssuesObserver is used by [ArchivePresenter]
 * to observe [ArchiveDataController]'s issueStubList and bitmaps.
 * It updates [ArchiveListAdapter]
 */
class ArchiveIssueStubsObserver(
    private val archivePresenter: ArchivePresenter
) : Observer<List<IssueStub>?> {

    override fun onChanged(issueStubs: List<IssueStub>?) {
        archivePresenter.getView()?.apply {
            archivePresenter.viewModel?.getMomentBitmapMap()?.let {
                addBitmaps(it)
            }
            onDataSetChanged(issueStubs ?: emptyList())
        }
    }

}
