package de.taz.app.android.api

import de.taz.app.android.util.reportAndRethrowExceptions
import de.taz.app.android.util.reportAndRethrowExceptionsAsync
import io.ktor.client.features.HttpRequestTimeoutException
import io.ktor.client.statement.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.CancellationException
import java.io.EOFException
import java.net.*
import java.net.SocketTimeoutException
import java.nio.channels.UnresolvedAddressException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.jvm.Throws

private val networkExceptions = listOf(
    ConnectException::class,
    SocketTimeoutException::class,
    HttpRequestTimeoutException::class,
    GraphQlClient.GraphQlRecoverableServerException::class,
    UnknownHostException::class,
    SSLException::class,
    EOFException::class,
    SSLHandshakeException::class,
    SocketException::class,
    UnresolvedAddressException::class,
    ConnectTimeoutException::class,
    NoRouteToHostException::class,
    SSLPeerUnverifiedException::class
)

sealed class ConnectivityException(
    message: String? = null,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    open class Recoverable(
        message: String,
        cause: Throwable? = null
    ) : ConnectivityException(message, cause)

    open class Unrecoverable(
        message: String,
        cause: Throwable? = null
    ) : ConnectivityException(message, cause)

    class NoInternetException(
        message: String = "Could not connect to server",
        cause: Throwable? = null
    ) : Recoverable(message, cause)

    class ImplementationException(
        message: String = "Unexpected server response or malformed query",
        cause: Throwable? = null,
        val response: HttpResponse? = null
    ) : Unrecoverable(message, cause)

    class ServerUnavailableException(
        message: String = "Server returned >500 error",
        cause: Throwable? = null
    ) : Recoverable(message, cause)
}


@Throws(ConnectivityException::class)
suspend fun <T> transformToConnectivityException(block: suspend () -> T): T {
    try {
        return block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: ConnectivityException) {
        // If it's already a ConnectivityException pass it along
        throw e
    } catch (e: Exception) {
        if (networkExceptions.contains(e::class)) {
            throw ConnectivityException.NoInternetException(cause = e)
        } else {
            // We want to know about non-connection or cancellation exceptions
            reportAndRethrowExceptions {
                throw ConnectivityException.ImplementationException(cause = e)
            }
        }
    }
}
