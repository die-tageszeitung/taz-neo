package de.taz.app.android.ui.login

import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.UiThread
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.AuthHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginPresenter(
    private val apiService: ApiService = ApiService.getInstance(),
    private val authHelper: AuthHelper = AuthHelper.getInstance(),
    private val issueRepository: IssueRepository = IssueRepository.getInstance()
) : BasePresenter<LoginContract.View, LoginDataController>(LoginDataController::class.java),
    LoginContract.Presenter {

    override fun onViewCreated(savedInstanceState: Bundle?) {
        getView()?.apply {
            viewModel?.observeAuthStatus(getLifecycleOwner()) { authStatus ->
                when (authStatus) {
                    AuthStatus.valid -> {
                        getMainView()?.apply {
                            setDrawerIssue(null)
                            showToast(R.string.toast_login_successfull)
                            getMainView()?.showHome()
                        }
                        getMainView()?.getLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.IO) {
                            DownloadService.cancelAllDownloads()
                            downloadLatestIssueMoment()
                            deletePublicIssues()
                        }
                    }
                    AuthStatus.notValid -> {
                        getMainView()?.showToast(R.string.toast_login_failed)
                    }
                    AuthStatus.elapsed -> {
                        getMainView()?.showToast(R.string.toast_login_elapsed)
                    }
                }
            }
            hideLoadingScreen()
        }
    }

    override fun onBackPressed(): Boolean {
        getView()?.getMainView()?.showHome()
        return true
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        if (menuItem.itemId == R.id.bottom_navigation_action_home) {
            getView()?.getMainView()?.showHome()
        }
    }

    override fun login(username: String, password: String) {
        getView()?.getLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.Default) {
            apiService.authenticate(username, password).let {
                authHelper.authStatusLiveData.postValue(it.authInfo.status)
                authHelper.tokenLiveData.postValue(it.token ?: "")
            }
        }
    }

    @UiThread
    private suspend fun downloadLatestIssueMoment() {
        val issue = ApiService.getInstance().getIssueByFeedAndDate()
        issueRepository.save(issue)
        getView()?.getMainView()?.apply {
            setDrawerIssue(issue)
            getApplicationContext().let {
                DownloadService.download(it, issue.moment)
            }
        }
    }

    @UiThread
    private fun deletePublicIssues() {
        issueRepository.deletePublicIssues()
    }
}