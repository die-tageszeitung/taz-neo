package de.taz.app.android.singletons

import android.content.Context
import androidx.lifecycle.ViewModel
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.AcceptHeaderInterceptor
import de.taz.app.android.api.AuthenticationHeaderInterceptor
import de.taz.app.android.util.*
import java.util.concurrent.TimeUnit

@Mockable
class OkHttp private constructor(applicationContext: Context) : ViewModel() {

    companion object : SingletonHolder<OkHttp, Context>(::OkHttp)

    val client = okhttp3.OkHttpClient
        .Builder()
        .addInterceptor(AcceptHeaderInterceptor())
        .addInterceptor(AuthenticationHeaderInterceptor(AuthHelper.getInstance(applicationContext)))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()


}
