package de.taz.app.android.ui.archive

import androidx.lifecycle.Observer
import de.taz.app.android.api.models.IssueStub

class ArchiveIssuesObserver(private val archivePresenter: ArchivePresenter) :
    Observer<List<IssueStub>?> {

    override fun onChanged(issueStubs: List<IssueStub>?) {
        archivePresenter.getView()?.apply {
            archivePresenter.viewModel?.getMomentBitmapMap()?.let {
                addBitmaps(it)
            }
            onDataSetChanged(issueStubs ?: emptyList())
        }
    }

}
