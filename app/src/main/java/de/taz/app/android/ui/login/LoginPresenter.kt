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
        try {
            if (username.isBlank()) {
                getView()?.showWrongUsername()
            } else if (password.isEmpty()) {
                getView()?.showWrongPassword()
            } else if (android.util.Patterns.EMAIL_ADDRESS.matcher(username).matches()) {
                lifecycleScope?.launch(Dispatchers.Default) {
                    apiService.authenticate(username, password)?.let {
                        CoroutineScope(Dispatchers.Main).launch {
                            authHelper.authStatus = it.authInfo.status
                            authHelper.token = it.token ?: ""
                            apiService.sendNotificationInfo()
                        }
                    } ?: run {
                        getView()?.getMainView()?.showToast(R.string.something_went_wrong_try_later)
                    }
                }
            } else if (username.toIntOrNull() != null) {
                lifecycleScope?.launch(Dispatchers.Default) {
                    apiService.checkSubscriptionId(username.toInt(), password)?.let { authInfo ->
                        when (authInfo.status) {
                            AuthStatus.valid -> {
                                // TODO =?

                            }
                            AuthStatus.notValid -> {
                                // TODO show error?!
                            }

                            AuthStatus.elapsed -> {
                                // TODO ? Show
                                getView()?.showSubscriptionElapsed()
                                /* Ihr taz-Digiabo ist leider inaktiv (weil es bspw. abgelaufen ist).

                                Bitte kontaktieren Sie unseren Service:

                                DIGIABO@TAZ.DE

                                */
                             }

                            AuthStatus.tazIdNotLinked -> {
                                getView()?.showLoginWithEmail()

                                // TODO
                                /*
                                    Um die neue App zu nutzen müssen sie sich zukünftig mit Ihrer E-Mail-Adresse und selbst gewähltem Passwort einloggen.

                                    Email & PAsswrod-Form

                                    Sollten Sie bereits bei taz.de mit E-Mail-Adresse und Passwort registriert sein (um dort Text zu kommentieren oder das Archiv zu nutzen) geben Sie hier bitte dies Daten an.

                                    Haben Sie ihr Passwort vergessen?
                                    HIER KLICKEN

                                    Ich akzeptiere die AGB sowie die Hinweise zum Wiederruf und Datenschutz

                                    ANMELDEN

                                 */
                            }
                        }
                    }
                }
            } else {
                getView()?.showWrongUsername()
            }
        } catch (e: ApiService.ApiServiceException.NoInternetException) {
            getView()?.getMainView()?.showToast(R.string.toast_no_internet)
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