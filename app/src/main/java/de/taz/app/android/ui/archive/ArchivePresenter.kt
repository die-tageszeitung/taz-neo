package de.taz.app.android.ui.archive

import android.graphics.Bitmap
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.webview.SectionWebViewFragment
import de.taz.app.android.util.ToastHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchivePresenter(
    private val apiService: ApiService = ApiService.getInstance(),
    private val issueRepository: IssueRepository = IssueRepository.getInstance()
) : BasePresenter<ArchiveContract.View, ArchiveDataController>(
    ArchiveDataController::class.java
), ArchiveContract.Presenter {

    private var feedName = "taz"

    override fun onViewCreated() {
        getView()?.let { view ->
            view.getLifecycleOwner().let {
                viewModel?.observeIssueStubs(it, ArchiveIssueStubsObserver(this))
            }
        }
    }

    override fun onItemSelected(issueStub: IssueStub) {
        getView()?.apply {
            showProgressbar(issueStub)

            getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {

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

    override fun onRefresh() {
        // check for new issues and download
        getView()?.getLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
            apiService.getIssuesByFeedAndDate(feedName)
            issueRepository.save(apiService.getIssuesByFeedAndDate(feedName))
            getView()?.hideRefreshLoadingIcon()
        } ?: getView()?.hideRefreshLoadingIcon()
    }

    override fun getNextIssueMoments(date: String, limit: Int) {
        val mainView = getView()?.getMainView()

        getView()?.getLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
            try {
                val issues = apiService.getIssuesByFeedAndDate(issueDate = date, limit = limit)
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