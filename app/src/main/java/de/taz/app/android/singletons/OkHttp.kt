package de.taz.app.android.singletons

import android.content.Context
import androidx.lifecycle.ViewModel
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.download.CONCURRENT_DOWNLOAD_LIMIT
import de.taz.app.android.util.*
import okhttp3.*
import java.util.concurrent.TimeUnit

@Mockable
class OkHttp private constructor(applicationContext: Context) : ViewModel() {

    companion object : SingletonHolder<OkHttp, Context>(::OkHttp)

    val client: OkHttpClient

    init {
        val builder = OkHttpClient
            .Builder()
            .retryOnConnectionFailure(true)
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
