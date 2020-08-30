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
import kotlinx.coroutines.*
import okhttp3.Request
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

const val DEFAULT_CONNECTION_CHECK_INTERVAL = 1000L
const val MAX_CONNECTION_CHECK_INTERVAL = 30000L

@Mockable
class ServerConnectionHelper private constructor(applicationContext: Context) {

    companion object : SingletonHolder<ServerConnectionHelper, Context>(::ServerConnectionHelper)

    private val toastHelper = ToastHelper.getInstance(applicationContext)
    private val okHttpClient = OkHttp.client

    private val appInfoRepository = AppInfoRepository.getInstance(applicationContext)

    val isDownloadServerReachableLiveData = MutableLiveData(true)
    val isGraphQlServerReachableLiveData = MutableLiveData(true)
    private var isDownloadServerReachableLastChecked: Long = 0L
    private var isGraphQlServerReachableLastChecked: Long = 0L

    private var backOffTimeMillis = 1000L

    var isDownloadServerReachable: Boolean
        get() = isDownloadServerReachableLiveData.value ?: true
        set(value) {
            if (!value) {
                if (isDownloadServerReachableLiveData.value != value
                    && isDownloadServerReachableLastChecked < Date().time - DEFAULT_CONNECTION_CHECK_INTERVAL
                ) {
                    isDownloadServerReachableLiveData.postValue(value)
                }
            } else {
                isDownloadServerReachableLiveData.postValue(value)
            }
        }
    var isGraphQlServerReachable: Boolean
        get() = isGraphQlServerReachableLiveData.value ?: true
        set(value) {
            if (!value) {
                if (isGraphQlServerReachableLiveData.value != value
                    && isDownloadServerReachableLastChecked < Date().time - DEFAULT_CONNECTION_CHECK_INTERVAL
                ) {
                    isGraphQlServerReachableLiveData.postValue(value)
                }
            } else {
                isGraphQlServerReachableLiveData.postValue(value)
            }
        }

    init {
        Transformations.distinctUntilChanged(isDownloadServerReachableLiveData)
            .observeForever { isConnected ->
                if (isConnected == false) {
                    toastHelper.showNoConnectionToast()
                    checkServerConnection()
                }
            }
    }

    private fun checkServerConnection() = CoroutineScope(Dispatchers.IO).launch {
        while (!this@ServerConnectionHelper.isDownloadServerReachable &&
                !this@ServerConnectionHelper.isGraphQlServerReachable
        ) {
            delay(backOffTimeMillis)
            try {
                log.debug("checking download server connection")
                val downloadServerUrl = appInfoRepository.get()?.globalBaseUrl ?: ""
                val downloadServerResult = awaitCallback(
                    okHttpClient.newCall(
                        Request.Builder().url(downloadServerUrl).build()
                    )::enqueue
                )
                val isDownloadServerReachable = downloadServerResult.isSuccessful
                if (isDownloadServerReachable) {
                    log.debug("download server reached")
                    withContext(Dispatchers.Main) {
                        isDownloadServerReachableLiveData.value = isDownloadServerReachable
                    }
                    isDownloadServerReachableLastChecked = Date().time
                    backOffTimeMillis = DEFAULT_CONNECTION_CHECK_INTERVAL
                    break
                } else if (downloadServerResult.code.toString().firstOrNull() in listOf('4', '5')) {
                    backOffTimeMillis = (backOffTimeMillis * 2).coerceAtMost(
                        MAX_CONNECTION_CHECK_INTERVAL
                    )
                }
                log.debug("checking graph ql server connection")
                val graphQlServerResult = awaitCallback(
                    okHttpClient.newCall(
                        Request.Builder().url(GRAPHQL_ENDPOINT).build()
                    )::enqueue
                )
                val isGraphQlServerReachable =  graphQlServerResult.isSuccessful
                if (isGraphQlServerReachable) {
                    log.debug("graph ql server reached")
                    withContext(Dispatchers.Main) {
                        isGraphQlServerReachableLiveData.value = isGraphQlServerReachable
                    }
                    isGraphQlServerReachableLastChecked = Date().time
                    backOffTimeMillis = DEFAULT_CONNECTION_CHECK_INTERVAL
                    break
                } else if (downloadServerResult.code.toString().firstOrNull() in listOf('4', '5')) {
                    backOffTimeMillis = (backOffTimeMillis * 2).coerceAtMost(
                        MAX_CONNECTION_CHECK_INTERVAL
                    )
                }
            } catch (ce: ConnectionException) {
                log.debug("could not reach download server - ConnectionException: ${ce.localizedMessage}")
            } catch (ste: SocketTimeoutException) {
                log.debug("could not reach download server - SocketTimeOutException: ${ste.localizedMessage}")
            } catch (uhe: UnknownHostException) {
                log.debug("could not reach download server - UnknownHostException: ${uhe.localizedMessage}")
            } catch (ce: ConnectException) {
                log.debug("could not reach download server - ConnectException: ${ce.localizedMessage}")
            } catch (eofe: EOFException) {
                log.debug("could not reach download server - EOFException: ${eofe.localizedMessage}")
            } catch (se: SSLException) {
                log.debug("could not reach download server - SSLException: ${se.localizedMessage}")
            } catch (she: SSLHandshakeException) {
                log.debug("could not reach download server - SSLHandshakeException: ${she.localizedMessage}")
            }
        }
        while (!this@ServerConnectionHelper.isGraphQlServerReachable) {
            delay(backOffTimeMillis)
            try {
                log.debug("checking graph ql server connection")
                val serverUrl = GRAPHQL_ENDPOINT
                val result = awaitCallback(
                    okHttpClient.newCall(
                        Request.Builder().url(serverUrl).build()
                    )::enqueue
                )
                log.warn("!!! RESULT: $result")
                val bool = result.isSuccessful
                if (bool) {
                    log.debug("graph ql reached")
                    withContext(Dispatchers.Main) {
                        isDownloadServerReachableLiveData.value = bool
                    }
                    isGraphQlServerReachableLastChecked = Date().time
                    backOffTimeMillis = DEFAULT_CONNECTION_CHECK_INTERVAL
                    break
                } else if (result.code.toString().firstOrNull() in listOf('4', '5')) {
                    backOffTimeMillis = (backOffTimeMillis * 2).coerceAtMost(
                        MAX_CONNECTION_CHECK_INTERVAL
                    )
                }
            } catch (ce: ConnectionException) {
                log.debug("could not reach download server - ConnectionException: ${ce.localizedMessage}")
            } catch (ste: SocketTimeoutException) {
                log.debug("could not reach download server - SocketTimeOutException: ${ste.localizedMessage}")
            } catch (uhe: UnknownHostException) {
                log.debug("could not reach download server - UnknownHostException: ${uhe.localizedMessage}")
            } catch (ce: ConnectException) {
                log.debug("could not reach download server - ConnectException: ${ce.localizedMessage}")
            } catch (eofe: EOFException) {
                log.debug("could not reach download server - EOFException: ${eofe.localizedMessage}")
            } catch (se: SSLException) {
                log.debug("could not reach download server - SSLException: ${se.localizedMessage}")
            } catch (she: SSLHandshakeException) {
                log.debug("could not reach download server - SSLHandshakeException: ${she.localizedMessage}")
            }
        }
    }
}