package de.taz.app.android.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.ToastHelper
import kotlinx.android.synthetic.main.fragment_login.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginFragment : Fragment() {

    private val authHelper = AuthHelper.getInstance()
    private val toastHelper = ToastHelper.getInstance()
    private val issueRepository = IssueRepository.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragment_login_login_button.setOnClickListener {
            lifecycleScope.launch(Dispatchers.Default) {
                authHelper.authTokenInfo.postValue(
                    ApiService.getInstance().authenticate(
                        fragment_login_username.text.toString(),
                        fragment_login_password.text.toString()
                    )
                )
            }
        }

        AuthHelper.getInstance().tokenLiveData.observe(this, Observer { token ->
            if (!token.isNullOrBlank()) {
                toastHelper.makeToast("logged in")
            }
        })

        if (authHelper.authTokenInfo.value?.authInfo?.status != AuthStatus.valid) {
            authHelper.authTokenInfo.observe(this, Observer { authTokenInfo ->
                authTokenInfo?.let {
                    when (authTokenInfo.authInfo.status) {
                        AuthStatus.valid -> {
                            toastHelper.makeToast(R.string.toast_login_successfull)
                            lifecycleScope.launch(Dispatchers.IO) {
                                issueRepository.getLatestIssue()?.let { latestIssue ->
                                   issueRepository.delete(latestIssue)
                                }

                                activity?.let { activity ->
                                    val issue = ApiService.getInstance().getIssueByFeedAndDate()
                                    issueRepository.save(issue)
                                    DownloadService.download(activity.applicationContext, issue)

                                    lifecycleScope.launch {
                                        lifecycleScope.launch {
                                            val isDownloadedLiveData =
                                                issue.isDownloadedLiveData()
                                            isDownloadedLiveData.observe(
                                                activity,
                                                Observer { downloaded ->
                                                    if (downloaded) {
                                                        /* TODO ViewModelProviders.of(activity)
                                                            .get(SelectedIssueViewModel::class.java)
                                                            .selectedIssue.postValue(issue)*/
                                                        toastHelper.makeToast("issue downloaded")
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        AuthStatus.elapsed -> {
                            toastHelper.makeToast(R.string.toast_login_elapsed)
                        }
                        AuthStatus.notValid -> {
                            toastHelper.makeToast(R.string.toast_login_failed)
                        }
                    }

                }
            })
        }
    }

}