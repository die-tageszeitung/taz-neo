package de.taz.app.android.download

import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.transformToConnectivityException
import de.taz.app.android.data.ConnectionHelper
import de.taz.app.android.data.HTTP_CLIENT_ENGINE
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.http.Url

class DownloadConnectionHelper(
    private val downloadEndpoint: String,
) : ConnectionHelper() {
    override suspend fun checkConnectivity(): Boolean {
        return try {
            transformToConnectivityException {
                val response = HttpClient(HTTP_CLIENT_ENGINE).get(Url(downloadEndpoint))
                response.status.value in 200..299
            }
        } catch (e: ConnectivityException.Recoverable) {
            false
        }
    }
}