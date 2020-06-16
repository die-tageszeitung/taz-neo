package de.taz.app.android.singletons

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.GRAPHQL_ENDPOINT
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

class InternetHelper private constructor(applicationContext: Context) {

    companion object : SingletonHolder<InternetHelper, Context>(::InternetHelper)

    private val toastHelper = ToastHelper.getInstance(applicationContext)
    private val okHttpClient = OkHttp.getInstance(applicationContext).client

    val canReachDownloadServerLiveData = MutableLiveData(false)

    var canReachDownloadServer: Boolean
        get() = canReachDownloadServerLiveData.value ?: false
        set(value) {
            if (!value) {
                if (canReachDownloadServerLiveData.value != value
                    && canReachDownloadServerLastChanged < Date().time - CONNECTION_CHECK_INTERVAL
                ) {
                    canReachDownloadServerLiveData.postValue(value)
                }
            } else {
                canReachDownloadServerLiveData.postValue(value)
            }
        }

    private var canReachDownloadServerLastChanged: Long = 0L

    init {
        Transformations.distinctUntilChanged(canReachDownloadServerLiveData)
            .observeForever { isConnected ->
                if (isConnected == false) {
                    toastHelper.showNoConnectionToast()
                    checkDownloadServer()
                }
            }
    }

    private fun checkDownloadServer() = CoroutineScope(Dispatchers.IO).launch {
        while (!this@InternetHelper.canReachDownloadServer) {
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
                    canReachDownloadServerLiveData.postValue(bool)
                    canReachDownloadServerLastChanged = Date().time
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