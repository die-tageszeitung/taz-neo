package de.taz.app.android.ui.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.util.AuthHelper
import de.taz.app.android.util.ToastHelper
import kotlinx.android.synthetic.main.fragment_login.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginFragment: Fragment() {

    private val authHelper = AuthHelper.getInstance()
    private val toastHelper = ToastHelper.getInstance()

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
            CoroutineScope(Dispatchers.Default).launch {
                authHelper.authTokenInfo.postValue(ApiService.getInstance().authenticate(
                    fragment_login_username.text.toString(),
                    fragment_login_password.text.toString()
                ))
            }
        }

        AuthHelper.getInstance().tokenLiveData.observe(this, Observer { token ->
            if(!token.isNullOrBlank()) {
                toastHelper.makeToast("logged in")
            }
        })

        if (authHelper.authTokenInfo.value?.authInfo?.status != AuthStatus.valid) {
            authHelper.authTokenInfo.observe(this, Observer { authTokenInfo ->
                authTokenInfo?.let {
                    when (authTokenInfo.authInfo.status) {
                        AuthStatus.valid -> {
                            toastHelper.makeToast(R.string.toast_login_successfull)
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