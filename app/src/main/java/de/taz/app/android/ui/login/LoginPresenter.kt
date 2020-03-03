package de.taz.app.android.ui.login

import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginPresenter(
    private val apiService: ApiService = ApiService.getInstance(),
    private val authHelper: AuthHelper = AuthHelper.getInstance(),
    private val issueRepository: IssueRepository = IssueRepository.getInstance()
) : BasePresenter<LoginContract.View, LoginDataController>(LoginDataController::class.java),
    LoginContract.Presenter {

    override fun onViewCreated(savedInstanceState: Bundle?) {
        var isFirstTime = true
        getView()?.apply {
            viewModel?.observeAuthStatus(getLifecycleOwner()) { authStatus ->
                if (!isFirstTime) {
                    when (authStatus) {
                        AuthStatus.valid -> {
                            getMainView()?.apply {
                                setDrawerIssue(null)
                                showToast(R.string.toast_login_successfull)
                                getMainView()?.showHome()
                            }
                            getMainView()?.getLifecycleOwner()
                                ?.lifecycleScope?.launch(Dispatchers.IO) {
                                DownloadService.cancelAllDownloads()
                                downloadLatestIssueMoments()
                                deletePublicIssues()
                            }
                        }
                        AuthStatus.notValid -> {
                            getMainView()?.showToast(R.string.toast_login_failed)
                        }
                        AuthStatus.elapsed -> {
                            getMainView()?.showToast(R.string.toast_login_elapsed)
                        }
                        AuthStatus.tazIdNotLinked -> {
                            // TODO
                        }
                    }
                } else {
                    isFirstTime = false
                }
            }
            hideLoadingScreen()
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        if (menuItem.itemId == R.id.bottom_navigation_action_home) {
            getView()?.getMainView()?.showHome()
        }
    }

    override fun login(username: String, password: String) {
        val lifecycleScope = getView()?.getLifecycleOwner()?.lifecycleScope
        lifecycleScope?.launch(Dispatchers.Default) {
            try {
                apiService.authenticate(username, password)?.let {
                    CoroutineScope(Dispatchers.Main).launch {
                        authHelper.authStatus = it.authInfo.status
                        authHelper.token = it.token ?: ""
                        apiService.sendNotificationInfo()
                    }
                } ?: run {
                    getView()?.getMainView()?.showToast(R.string.something_went_wrong_try_later)
                }
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                getView()?.getMainView()?.showToast(R.string.toast_no_internet)
            }
        }
    }

    private suspend fun downloadLatestIssueMoments() {
        ApiService.getInstance().getIssuesByDate().forEach { issue ->
            issueRepository.save(issue)
            getView()?.getMainView()?.apply {
                setDrawerIssue(issue)
                getApplicationContext().let {
                    DownloadService.download(it, issue.moment)
                }
            }
        }
    }

    private fun deletePublicIssues() {
        issueRepository.deletePublicIssues()
    }
}