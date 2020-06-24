package de.taz.app.android.singletons

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.GRAPHQL_ENDPOINT
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.persistence.repository.AppInfoRepository
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.awaitCallback
import io.sentry.connection.ConnectionException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*

const val CONNECTION_CHECK_INTERVAL = 1000

@Mockable
class ServerConnectionHelper private constructor(applicationContext: Context) {

    companion object : SingletonHolder<ServerConnectionHelper, Context>(::ServerConnectionHelper)

    private val toastHelper = ToastHelper.getInstance(applicationContext)
    private val okHttpClient = OkHttp.getInstance(applicationContext).client

    private val appInfoRepository = AppInfoRepository.getInstance(applicationContext)

    val isGraphQlServerReachableLiveData = MutableLiveData(true)
    private var isGraphQlServerReachableLastChecked: Long = 0L

    var isGraphQlServerReachable: Boolean
        get() = isGraphQlServerReachableLiveData.value ?: true
        set(value) {
            if (!value) {
                if (isGraphQlServerReachableLiveData.value != value
                    && isGraphQlServerReachableLastChecked < Date().time - CONNECTION_CHECK_INTERVAL
                ) {
                    isGraphQlServerReachableLiveData.postValue(value)
                }
            } else {
                isGraphQlServerReachableLiveData.postValue(value)
            }
        }


    val isDownloadServerServerReachableLiveData = MutableLiveData(true)
    private var isDownloadServerServerReachableLastChecked: Long = 0L

    var isDownloadServerServerReachable: Boolean
        get() = isDownloadServerServerReachableLiveData.value ?: true
        set(value) {
            if (!value) {
                if (isDownloadServerServerReachableLiveData.value != value
                    && isDownloadServerServerReachableLastChecked < Date().time - CONNECTION_CHECK_INTERVAL
                ) {
                    isDownloadServerServerReachableLiveData.postValue(value)
                }
            } else {
                isDownloadServerServerReachableLiveData.postValue(value)
            }
        }

    init {
        Transformations.distinctUntilChanged(isGraphQlServerReachableLiveData)
            .observeForever { isConnected ->
                if (isConnected == false) {
                    toastHelper.showNoConnectionToast()
                    checkGraphQlServer()
                }
            }
        Transformations.distinctUntilChanged(isDownloadServerServerReachableLiveData)
            .observeForever { isConnected ->
                if (isConnected == false) {
                    toastHelper.showNoConnectionToast()
                    checkDownloadServerServer()
                }
            }
    }

    private fun checkGraphQlServer() = CoroutineScope(Dispatchers.IO).launch {
        while (!this@ServerConnectionHelper.isGraphQlServerReachable) {
            delay(1000)
            try {
                val result = awaitCallback(
                    okHttpClient.newCall(
                        Request.Builder().url(GRAPHQL_ENDPOINT).build()
                    )::enqueue
                )
                val bool = result.code.toString().startsWith("2")
                if (bool) {
                    log.debug("downloadserver reached")
                    isGraphQlServerReachableLiveData.postValue(bool)
                    isGraphQlServerReachableLastChecked = Date().time
                }
            } catch (ce: ConnectionException) {
                log.debug("could not reach download server - ConnectionException: ${ce.localizedMessage}")
            } catch (ste: SocketTimeoutException) {
                log.debug("could not reach download server - SocketTimeOutException: ${ste.localizedMessage}")
            } catch (uhe: UnknownHostException) {
                log.debug("could not reach download server - UnknownHostException: ${uhe.localizedMessage}")
            }
        }
    }

    private fun checkDownloadServerServer() = CoroutineScope(Dispatchers.IO).launch {
        while (!this@ServerConnectionHelper.isDownloadServerServerReachable) {
            delay(1000)
            try {
                appInfoRepository.get()?.let {
                    val result = awaitCallback(
                        okHttpClient.newCall(
                            Request.Builder().url(it.globalBaseUrl).build()
                        )::enqueue
                    )
                    val bool = result.code.toString().startsWith("2")
                    if (bool) {
                        log.debug("downloadserver reached")
                        isDownloadServerServerReachableLiveData.postValue(bool)
                        isDownloadServerServerReachableLastChecked = Date().time
                    }
                }
            } catch (ce: ConnectionException) {
                log.debug("could not reach download server - ConnectionException: ${ce.localizedMessage}")
            } catch (ste: SocketTimeoutException) {
                log.debug("could not reach download server - SocketTimeOutException: ${ste.localizedMessage}")
            } catch (uhe: UnknownHostException) {
                log.debug("could not reach download server - UnknownHostException: ${uhe.localizedMessage}")
            }
        }
    }
}