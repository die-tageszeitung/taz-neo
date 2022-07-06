package de.taz.app.android.download

import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.transformToConnectivityException
import de.taz.app.android.data.ConnectionHelper
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.request.*
import io.ktor.http.*

class DownloadConnectionHelper(
    private val downloadEndpoint: String,
): ConnectionHelper() {
    override suspend fun checkConnectivity(): Boolean {
        return try {
            transformToConnectivityException {
                val response = HttpClient(Android).get(Url(downloadEndpoint))
                response.status.value in 200..299
            }
        } catch (e: ConnectivityException.Recoverable) {
            false
        }
    }
}