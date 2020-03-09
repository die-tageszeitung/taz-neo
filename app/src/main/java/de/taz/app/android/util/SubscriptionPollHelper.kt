package de.taz.app.android.util

import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.SubscriptionStatus
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.observeDistinctOnce
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToastHelper
import io.sentry.Sentry
import kotlinx.coroutines.*

class SubscriptionPollHelper private constructor(applicationContext: Context) : ViewModel() {

    private val log by Log

    companion object : SingletonHolder<SubscriptionPollHelper, Context>(::SubscriptionPollHelper)

    private val apiService = ApiService.getInstance(applicationContext)
    private val authHelper = AuthHelper.getInstance(applicationContext)
    private val toastHelper = ToastHelper.getInstance(applicationContext)
    private val issueRepository = IssueRepository.getInstance(applicationContext)

    init {
        authHelper.isPollingLiveData.observeDistinct(ProcessLifecycleOwner.get()) { isPolling ->
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
                        authHelper.isPolling = false
                        authHelper.token = subscriptionInfo.token!!
                        authHelper.authStatus = AuthStatus.valid
                        CoroutineScope(Dispatchers.Main).launch {
                            authHelper.authStatusLiveData.observeDistinctOnce(ProcessLifecycleOwner.get()) {
                                launch(Dispatchers.IO) {
                                    issueRepository.save(apiService.getLastIssues())
                                }
                            }
                        }
                        toastHelper.showToast(R.string.toast_login_successful)
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
                        toastHelper.showToast(R.string.toast_login_failed_retry)
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
                        toastHelper.showToast(R.string.toast_login_failed_retry)
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