package de.taz.app.android.singletons

import android.content.Context
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.SubscriptionStatus
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val POLLING_TIMEOUT_INITIAL_MS = 100L
private const val POLLING_TIMEOUT_MAX_MS = 3600000L // 1 hour

class SubscriptionPollHelper private constructor(applicationContext: Context) {

    companion object : SingletonHolder<SubscriptionPollHelper, Context>(::SubscriptionPollHelper)

    private val apiService = ApiService.getInstance(applicationContext)
    private val authHelper = AuthHelper.getInstance(applicationContext)
    private val toastHelper = ToastHelper.getInstance(applicationContext)

    private val scope = CoroutineScope(Dispatchers.Default)
    private var pollingJob: Job? = null

    init {
        scope.launch {
            authHelper.isPollingForConfirmationEmail.asFlow()
                .distinctUntilChanged()
                .collect {
                    if (it) {
                        poll()
                    }
                }
        }
    }

    private fun poll(timeoutMillis: Long = POLLING_TIMEOUT_INITIAL_MS) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            val timeMillis = timeoutMillis.coerceAtMost(POLLING_TIMEOUT_MAX_MS)
            delay(timeMillis)

            try {
                val subscriptionInfo = apiService.subscriptionPoll()
                log.debug("poll subscriptionPoll: $subscriptionInfo")

                when (subscriptionInfo?.status) {
                    SubscriptionStatus.valid -> {
                        authHelper.isPollingForConfirmationEmail.set(false)
                        authHelper.token.set(requireNotNull(subscriptionInfo.token) {
                            "Backend returned empty token with SubscriptionStatus.valid"
                        })
                        authHelper.status.set(AuthStatus.valid)

                        toastHelper.showToast(R.string.toast_login_successful)
                    }
                    SubscriptionStatus.tazIdNotValid,
                    SubscriptionStatus.subscriptionIdNotValid,
                    SubscriptionStatus.elapsed,
                    SubscriptionStatus.invalidConnection,
                    SubscriptionStatus.noFirstName,
                    SubscriptionStatus.noSurname,
                    SubscriptionStatus.invalidMail,
                    SubscriptionStatus.alreadyLinked -> {
                        // stop polling and tell user to try again
                        toastHelper.showToast(R.string.toast_login_failed_retry)
                    }
                    SubscriptionStatus.waitForMail -> {
                        // still waiting poll again
                        poll(2 * timeMillis)
                    }
                    SubscriptionStatus.waitForProc -> {
                        // still waiting poll again
                        poll(timeMillis * 2)
                    }
                    SubscriptionStatus.noPollEntry -> {
                        // user waited to long
                        toastHelper.showToast(R.string.toast_login_failed_retry)
                    }
                    null -> {
                        // continue and wait for correct response
                        poll(timeMillis * 2)
                    }
                    SubscriptionStatus.tooManyPollTries -> {
                        authHelper.isPollingForConfirmationEmail.set(false)
                        SentryWrapper.captureMessage("TooManyPollTries")
                    }
                    else -> {
                        // should not happen
                        SentryWrapper.captureMessage("subscriptionPoll returned ${subscriptionInfo.status}")
                    }
                }
            } catch (e: ConnectivityException) {
                // continue polling
                poll(timeMillis * 2)
            }
        }
    }
}