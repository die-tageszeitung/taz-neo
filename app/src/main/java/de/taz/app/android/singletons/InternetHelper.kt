package de.taz.app.android.singletons

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import de.taz.app.android.GRAPHQL_ENDPOINT
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.awaitCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request

class InternetHelper private constructor(applicationContext: Context) {

    companion object : SingletonHolder<InternetHelper, Context>(::InternetHelper)

    private val okHttpClient = OkHttp.getInstance(applicationContext).client

    val internetLiveData = MutableLiveData(true)

    var hasNoInternet: Boolean
        set(value) = internetLiveData.postValue(value)
        get() = internetLiveData.value ?: false

    init {
        Transformations.distinctUntilChanged(internetLiveData).observeForever { hasInternet ->
            if (hasInternet == false) {
                CoroutineScope(Dispatchers.IO).launch {
                    delay(1000)
                    internetLiveData.postValue(awaitCallback {
                        okHttpClient.newCall(
                            Request.Builder().url(GRAPHQL_ENDPOINT).build()
                        )::enqueue
                    }.code.toString().startsWith("2"))
                }
            }
        }
    }
}