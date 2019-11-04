package de.taz.app.android.ui.archive

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

class ArchivePresenter : BasePresenter<ArchiveContract.View, ArchiveDataController>(
    ArchiveDataController::class.java
), ArchiveContract.Presenter {

    private var feedName = "taz"

    private val apiService = ApiService.getInstance()
    private val issueRepository = IssueRepository.getInstance()

    override fun onViewCreated() {
        getView()?.let { view ->
            view.getLifecycleOwner().let {
                viewModel?.observeIssues(it) { issues ->
                    view.onDataSetChanged(issues ?: emptyList())
                }
            }
        }
    }

    override fun onItemSelected(issueStub: IssueStub) {
        getView()?.getLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
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
                                showMainFragment(SectionWebViewFragment(firstSection))
                                firstSection.isDownloadedLiveData().removeObserver(this)
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        firstSection.isDownloadedLiveData().observe(getLifecycleOwner(), observer)
                    }
                }
            }
        }
    }

    override fun onRefresh() {
        // check for new issues and download
        getView()?.getLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
            val todaysIssue = apiService.getIssueByFeedAndDate(feedName)
            if (!issueRepository.exists(todaysIssue)) {
                issueRepository.save(todaysIssue)
                getView()?.getMainView()?.getApplicationContext()?.let {
                    DownloadService.download(it, todaysIssue.moment)
                }
            }
            getView()?.hideScrollView()
        } ?: getView()?.hideScrollView()
    }

    override fun downloadNextIssueMoments(date: String) {
        val toastHelper = ToastHelper.getInstance()

         getView()?.getLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
            try {
                val issues = apiService.getIssuesByFeedAndDate(issueDate = date, limit = 10)
                issueRepository.save(issues)
                getView()?.getMainView()?.getApplicationContext()?.let { applicationContext ->
                    issues.forEach { it.downloadMoment(applicationContext) }
                }
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                toastHelper.showNoConnectionToast()
            } catch (e: ApiService.ApiServiceException.InsufficientDataException) {
                toastHelper.makeToast(R.string.something_went_wrong_try_later)
            } catch (e: ApiService.ApiServiceException.WrongDataException) {
                toastHelper.makeToast(R.string.something_went_wrong_try_later)
            }
        }
    }
}