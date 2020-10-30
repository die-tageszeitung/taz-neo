package de.taz.app.android.singletons

import androidx.lifecycle.ViewModel
import de.taz.app.android.CONCURRENT_FILE_DOWNLOADS
import de.taz.app.android.annotation.Mockable
import okhttp3.*
import java.util.concurrent.TimeUnit

@Mockable
object OkHttp : ViewModel() {

    val client: OkHttpClient

    init {
        val builder = OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .dispatcher(Dispatcher().also { it.maxRequestsPerHost = CONCURRENT_FILE_DOWNLOADS })

        // disallow cleartext connections if not testing
        try {
            Class.forName("org.junit.Test")
            builder.connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
        } catch (e: ClassNotFoundException) {
            builder.connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        }

        client = builder.build()
    }
}
