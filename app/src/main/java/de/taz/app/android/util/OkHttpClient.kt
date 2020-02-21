package de.taz.app.android.util

import de.taz.app.android.api.AcceptHeaderInterceptor
import de.taz.app.android.api.AuthenticationHeaderInterceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * helper class to initialize the HttpClient
 */
val okHttpClient: OkHttpClient = OkHttpClient
    .Builder()
    .addInterceptor(AcceptHeaderInterceptor())
    .addInterceptor(AuthenticationHeaderInterceptor())
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()


