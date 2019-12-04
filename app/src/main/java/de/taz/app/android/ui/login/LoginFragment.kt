package de.taz.app.android.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.ToastHelper
import kotlinx.android.synthetic.main.fragment_login.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LoginFragment :
    BaseMainFragment<LoginContract.Presenter>(),
    BackFragment {

    override val presenter = LoginPresenter()

    private val presenter = LoginPresenter()
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
        presenter.attach(this)
        presenter.onViewCreated(savedInstanceState)
        fragment_login_login_button.setOnClickListener {
            web_view_spinner.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.Default) {
                authHelper.authTokenInfo.postValue(
                    ApiService.getInstance().authenticate(
                        fragment_login_username.text.toString(),
                        fragment_login_password.text.toString()
                    )
                )
            }
        }

        AuthHelper.getInstance().tokenLiveData.observe(viewLifecycleOwner, Observer { token ->
            if (!token.isNullOrBlank()) {
                toastHelper.makeToast("logged in")
            }
        })

        if (authHelper.authTokenInfo.value?.authInfo?.status != AuthStatus.valid) {
            authHelper.authTokenInfo.observe(viewLifecycleOwner, Observer { authTokenInfo ->
                authTokenInfo?.let {
                    when (authTokenInfo.authInfo.status) {
                        AuthStatus.valid -> {
                            toastHelper.makeToast(R.string.toast_login_successfull)
                            runBlocking(Dispatchers.IO) {
                                issueRepository.deleteAllIssues()
                                log.debug("ALL DELETED!")
                            }
                            toastHelper.makeToast(R.string.toast_login_successfull)
                        }
                        AuthStatus.elapsed -> {
                            toastHelper.makeToast(R.string.toast_login_elapsed)
                        }
                        AuthStatus.notValid -> {
                            toastHelper.makeToast(R.string.toast_login_failed)
                        }
                    }
                    web_view_spinner.visibility = View.GONE

                }
            })
        }
    }

    override fun onBackPressed(): Boolean {
        return presenter.onBackPressed()
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        presenter.onBottomNavigationItemClicked(menuItem)
    }

}