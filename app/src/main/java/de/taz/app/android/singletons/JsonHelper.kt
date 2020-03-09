package de.taz.app.android.singletons

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi

object JsonHelper {

    val moshi: Moshi = Moshi.Builder().build()

    inline fun <reified T> adapter(): JsonAdapter<T> {
        return moshi.adapter(T::class.java)
    }

    inline fun <reified T> toJson(item: T?): String {
        return adapter<T>().toJson(item)
    }
}