package de.taz.app.android.util

import android.content.Context
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.SubscriptionStatus
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SubscriptionPollHelper private constructor(applicationContext: Context) : ViewModel() {

    private val log by Log

    companion object : SingletonHolder<SubscriptionPollHelper, Context>(::SubscriptionPollHelper)

    private val apiService = ApiService.getInstance(applicationContext)
    private val authHelper = AuthHelper.getInstance(applicationContext)
    private val toastHelper = ToastHelper.getInstance(applicationContext)

    init {
        Transformations.distinctUntilChanged(
            authHelper.pollingLiveData
        ).observeForever { isPolling ->
            if (isPolling) {
                poll()
            }
        }
    }

    private fun poll(timeoutMillis: Long = 100) {
        CoroutineScope(Dispatchers.IO).launch {
            delay(timeoutMillis)

            try {
                val subscriptionInfo = apiService.subscriptionPoll()
                log.debug("poll subscriptionPoll: $subscriptionInfo")

                when (subscriptionInfo?.status) {
                    SubscriptionStatus.valid -> {
                        authHelper.pollingLiveData.postValue(false)
                        authHelper.tokenLiveData.postValue(subscriptionInfo.token!!)
                        authHelper.authStatusLiveData.postValue(AuthStatus.valid)
                        toastHelper.makeToast(R.string.toast_login_successful)
                    }
                    SubscriptionStatus.tazIdNotValid,
                    SubscriptionStatus.subscriptionIdNotValid,
                    SubscriptionStatus.elapsed,
                    SubscriptionStatus.invalidConnection,
                    SubscriptionStatus.invalidMail,
                    SubscriptionStatus.alreadyLinked -> {
                        // should never happen
                        Sentry.capture("subscriptionPoll returned ${subscriptionInfo.status}")
                        // stop polling and tell user to try again
                        toastHelper.makeToast(R.string.toast_login_failed_retry)
                    }
                    SubscriptionStatus.waitForMail -> {
                        // still waiting poll again
                        poll(2 * timeoutMillis)
                    }
                    SubscriptionStatus.waitForProc -> {
                        // still waiting poll again
                        poll(timeoutMillis * 2)
                    }
                    SubscriptionStatus.noPollEntry -> {
                        // user waited to long
                        toastHelper.makeToast(R.string.toast_login_failed_retry)
                    }
                    null -> {
                        // continue and wait for correct response
                        poll(timeoutMillis * 2)
                    }
                }
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                // continue polling
                poll(timeoutMillis * 2)
            }
        }
    }
}