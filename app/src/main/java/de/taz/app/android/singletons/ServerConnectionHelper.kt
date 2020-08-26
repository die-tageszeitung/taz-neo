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
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*

const val CONNECTION_CHECK_INTERVAL = 1000

@Mockable
class ServerConnectionHelper private constructor(applicationContext: Context) {

    companion object : SingletonHolder<ServerConnectionHelper, Context>(::ServerConnectionHelper)

    private val toastHelper = ToastHelper.getInstance(applicationContext)
    private val okHttpClient = OkHttp.client

    private val appInfoRepository = AppInfoRepository.getInstance(applicationContext)

    val isServerReachableLiveData = MutableLiveData(true)
    private var isServerReachableLastChecked: Long = 0L

    var isServerReachable: Boolean
        get() = isServerReachableLiveData.value ?: true
        set(value) {
            if (!value) {
                if (isServerReachableLiveData.value != value
                    && isServerReachableLastChecked < Date().time - CONNECTION_CHECK_INTERVAL
                ) {
                    isServerReachableLiveData.postValue(value)
                }
            } else {
                isServerReachableLiveData.postValue(value)
            }
        }

    init {
        Transformations.distinctUntilChanged(isServerReachableLiveData)
            .observeForever { isConnected ->
                if (isConnected == false) {
                    toastHelper.showNoConnectionToast()
                    checkServerConnection()
                }
            }
    }

    private fun checkServerConnection() = CoroutineScope(Dispatchers.IO).launch {
        while (!this@ServerConnectionHelper.isServerReachable) {
            delay(1000)
            try {
                log.debug("checking server connection")
                val serverUrl = appInfoRepository.get()?.globalBaseUrl ?: GRAPHQL_ENDPOINT
                val result = awaitCallback(
                    okHttpClient.newCall(
                        Request.Builder().url(serverUrl).build()
                    )::enqueue
                )
                val bool = result.code.toString().firstOrNull() in listOf('2', '3')
                if (bool) {
                    log.debug("downloadserver reached")
                    isServerReachableLiveData.postValue(bool)
                    isServerReachableLastChecked = Date().time
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
            }
        }
    }
}