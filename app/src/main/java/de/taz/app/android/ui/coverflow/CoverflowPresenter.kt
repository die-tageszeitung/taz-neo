package de.taz.app.android.ui.coverflow

import android.os.Bundle
import androidx.lifecycle.Observer
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CoverflowPresenter() :
    BasePresenter<CoverflowContract.View, CoverflowDataController>(CoverflowDataController::class.java),
    CoverflowContract.Presenter
{
    val log by Log

    private var lastIssueList: List<IssueStub>? = null

    private fun requireViewModel(): CoverflowContract.DataController {
        return viewModel!!
    }

    private fun requireView(): CoverflowContract.View {
        return getView()!!
    }

    private fun didIssueListChange(newIssueList: List<IssueStub>): Boolean {
        val lastIssueTags = lastIssueList?.map { it.tag }
        val newIssueTags = newIssueList.map { it.tag }

        if (lastIssueTags?.size != newIssueTags.size) return true

        val comparedTags = lastIssueTags.zip(newIssueTags) { a, b ->
            a == b
        }

        return comparedTags.any { !it }
    }

    override fun onViewCreated(savedInstanceState: Bundle?) {
        requireViewModel().visibleIssueStubsLiveData.observe(requireView().getLifecycleOwner(),
            Observer {
                // take feedname of first item
                it?.let {
                    val feed = requireViewModel().feeds.value?.get(it[0].feedName)
                    if (didIssueListChange(it)) {
                        lastIssueList = it
                        requireView().onDatasetChanged(lastIssueList!!, feed)
                        requireView().skipToEnd()
                    }
                }
            })
    }


}