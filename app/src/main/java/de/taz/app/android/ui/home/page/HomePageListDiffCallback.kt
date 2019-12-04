package de.taz.app.android.ui.home.page

import androidx.recyclerview.widget.DiffUtil
import de.taz.app.android.api.models.IssueStub

class HomePageListDiffCallback(
    private val oldIssueStubList: List<IssueStub> = emptyList(),
    private val newIssueStubList: List<IssueStub> = emptyList()
): DiffUtil.Callback() {

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldIssueStubList[oldItemPosition].tag == newIssueStubList[newItemPosition].tag
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldIssueStubList[oldItemPosition].tag == newIssueStubList[newItemPosition].tag
    }

    override fun getNewListSize(): Int = newIssueStubList.size

    override fun getOldListSize(): Int = oldIssueStubList.size

}