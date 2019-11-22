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

class CoverflowPresenter(
    private val apiService: ApiService = ApiService.getInstance(),
    private val feedRepository: FeedRepository = FeedRepository.getInstance(),
    private val issueRepository: IssueRepository = IssueRepository.getInstance()
) :
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

    override suspend fun onRefresh() {
        log.debug("onRefresh called")
        // check for new issues and download
        withContext(Dispatchers.IO) {
            try {
                feedRepository.save(apiService.getFeeds())
                issueRepository.save(apiService.getIssuesByDate())
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                getView()?.getMainView()?.showToast(R.string.toast_no_internet)
            } catch (e: ApiService.ApiServiceException.InsufficientDataException) {
                getView()?.getMainView()?.showToast(R.string.something_went_wrong_try_later)
            } catch (e: ApiService.ApiServiceException.WrongDataException) {
                getView()?.getMainView()?.showToast(R.string.something_went_wrong_try_later)
            }
        }
    }

}