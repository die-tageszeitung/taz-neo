package de.taz.app.android.singletons

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.GRAPHQL_ENDPOINT
import de.taz.app.android.annotation.Mockable
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

    val isConnectedLiveData = MutableLiveData(true)

    var isConnected: Boolean
        get() = isConnectedLiveData.value ?: true
        set(value) {
            if (!value) {
                if (isConnectedLiveData.value != value
                    && isConnectedLastChanged < Date().time - CONNECTION_CHECK_INTERVAL
                ) {
                    isConnectedLiveData.postValue(value)
                }
            } else {
                isConnectedLiveData.postValue(value)
            }
        }

    private var isConnectedLastChanged: Long = 0L

    init {
        Transformations.distinctUntilChanged(isConnectedLiveData)
            .observeForever { isConnected ->
                if (isConnected == false) {
                    toastHelper.showNoConnectionToast()
                    checkDownloadServer()
                }
            }
    }

    private fun checkDownloadServer() = CoroutineScope(Dispatchers.IO).launch {
        while (!this@ServerConnectionHelper.isConnected) {
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
                    isConnectedLiveData.postValue(bool)
                    isConnectedLastChanged = Date().time
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