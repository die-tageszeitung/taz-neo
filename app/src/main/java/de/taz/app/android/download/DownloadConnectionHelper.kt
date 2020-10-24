package de.taz.app.android.download

import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.transformToConnectivityException
import de.taz.app.android.data.ConnectionHelper
import de.taz.app.android.singletons.OkHttp
import de.taz.app.android.util.awaitCallback
import okhttp3.OkHttpClient
import okhttp3.Request

class DownloadConnectionHelper(
    private val downloadEndpoint: String,
    private val httpClient: OkHttpClient = OkHttp.client
): ConnectionHelper() {
    override suspend fun checkConnectivity(): Boolean {
        return try {
            transformToConnectivityException {
                val result = awaitCallback(
                    httpClient.newCall(
                        Request.Builder().url(downloadEndpoint).build()
                    )::enqueue
                )
                result.isSuccessful
            }
        } catch (e: ConnectivityException.Recoverable) {
            false
        }
    }

}