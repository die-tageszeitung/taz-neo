package de.taz.app.android.util

import android.content.Context
import de.taz.app.android.api.AcceptHeaderInterceptor
import de.taz.app.android.api.AuthenticationHeaderInterceptor
import de.taz.app.android.singletons.AuthHelper
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * helper class to initialize the HttpClient
 */
fun okHttpClient(applicationContext: Context? = null): OkHttpClient = OkHttpClient
    .Builder()
    .addInterceptor(AcceptHeaderInterceptor())
    .addInterceptor(AuthenticationHeaderInterceptor(AuthHelper.getInstance(applicationContext)))
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .build()


