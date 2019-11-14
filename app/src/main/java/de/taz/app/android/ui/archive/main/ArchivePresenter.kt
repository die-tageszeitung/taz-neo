package de.taz.app.android.ui.archive.main

import android.graphics.Bitmap
import androidx.lifecycle.Observer
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArchivePresenter(
    private val apiService: ApiService = ApiService.getInstance(),
    private val issueRepository: IssueRepository = IssueRepository.getInstance(),
    private val feedRepository: FeedRepository = FeedRepository.getInstance(),
    private val log: Log = Log(ArchivePresenter::javaClass.name)
) : BasePresenter<ArchiveContract.View, ArchiveDataController>(
    ArchiveDataController::class.java
), ArchiveContract.Presenter {

    override fun onViewCreated() {
        getView()?.let { view ->
            view.getLifecycleOwner().let {
                viewModel?.apply {
                    observeIssueStubs(it, ArchiveIssueStubsObserver(this@ArchivePresenter))
                    observeFeeds(it) { feeds ->
                        view.setFeeds(feeds ?: emptyList())
                    }
                    observeInactiveFeedNames(it) { feedNames ->
                        view.setInactiveFeedNames(feedNames)
                    }
                }
            }
        }

    }

    override suspend fun onItemSelected(issueStub: IssueStub) {
        log.debug("onItemSelected called")
        getView()?.apply {
            showProgressbar(issueStub)

            withContext(Dispatchers.IO) {
                val issue = issueRepository.getIssue(issueStub)

                getView()?.getMainView()?.apply {
                    // start download if not yet downloaded
                    if (!issue.isDownloadedOrDownloading()) {
                        getApplicationContext().let { applicationContext ->
                            DownloadService.download(applicationContext, issue)
                        }
                    }

                    // set main issue
                    getMainDataController().setIssue(issue)

                    issue.sectionList.first().let { firstSection ->
                        val observer = object : Observer<Boolean> {
                            override fun onChanged(isDownloaded: Boolean?) {
                                // open last clicked issue if downloaded
                                if (isDownloaded == true && getMainDataController().getIssue() == issue) {
                                    firstSection.isDownloadedLiveData().removeObserver(this)
                                    showMainFragment(SectionWebViewFragment(firstSection))
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            firstSection.isDownloadedLiveData()
                                .observe(getLifecycleOwner(), observer)
                        }
                    }
                }
            }
        }
    }

    override fun onMomentBitmapCreated(tag: String, bitmap: Bitmap) {
        viewModel?.addBitmap(tag, bitmap)
        getView()?.addBitmap(tag, bitmap)
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
            getView()?.hideRefreshLoadingIcon()
        }
    }

    override suspend fun getNextIssueMoments(date: String, limit: Int) {
        log.debug("getNextIssueMoments called")
        withContext(Dispatchers.IO) {
            val mainView = getView()?.getMainView()

            try {
                val issues = apiService.getIssuesByDate(issueDate = date, limit = limit)
                issueRepository.save(issues)
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                mainView?.showToast(R.string.toast_no_internet)
            } catch (e: ApiService.ApiServiceException.InsufficientDataException) {
                mainView?.showToast(R.string.something_went_wrong_try_later)
            } catch (e: ApiService.ApiServiceException.WrongDataException) {
                mainView?.showToast(R.string.something_went_wrong_try_later)
            }
        }
    }

}