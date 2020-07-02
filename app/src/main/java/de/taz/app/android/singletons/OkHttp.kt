package de.taz.app.android.singletons

import android.content.Context
import androidx.lifecycle.ViewModel
import de.taz.app.android.GRAPHQL_ENDPOINT
import de.taz.app.android.TAZ_AUTH_HEADER
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.download.CONCURRENT_DOWNLOAD_LIMIT
import de.taz.app.android.util.*
import okhttp3.*
import java.io.IOException
import java.lang.Exception
import java.util.concurrent.TimeUnit

@Mockable
class OkHttp private constructor(applicationContext: Context) : ViewModel() {

    companion object : SingletonHolder<OkHttp, Context>(::OkHttp)

    val client: OkHttpClient

    init {
        val builder = OkHttpClient
            .Builder()
            .addInterceptor(AcceptHeaderInterceptor())
            .addInterceptor(
                AuthenticationHeaderInterceptor(
                    AuthHelper.getInstance(
                        applicationContext
                    )
                )
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .dispatcher(Dispatcher().also { it.maxRequestsPerHost = CONCURRENT_DOWNLOAD_LIMIT })

        // disallow cleartext connections if not testing
        try {
            Class.forName("org.junit.Test")
        } catch (e: ClassNotFoundException) {
            builder.connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        }

        client = builder.build()
    }

}

/**
 * set ACCEPT headers needed by backend
 */
class AcceptHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        try {
            return chain.proceed(
                chain.request().newBuilder().addHeader("Accept", "application/json, */*").build()
            )
        } catch (e: Exception) {
            throw IOException(e.message, e)
        }
    }
}

/**
 * set authentication header if authenticated
 */
class AuthenticationHeaderInterceptor(private val authHelper: AuthHelper) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authHelper.token
        val originalRequest = chain.request()
        val request =
            if (originalRequest.url.toString() == GRAPHQL_ENDPOINT && token.isNotEmpty()) {
                originalRequest.newBuilder().addHeader(
                    TAZ_AUTH_HEADER,
                    token
                ).build()
            } else {
                originalRequest
            }
        try {
            return chain.proceed(request)
        } catch (e: Exception) {
            throw IOException(e.message, e)
        }
    }
}
