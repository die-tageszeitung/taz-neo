package de.taz.app.android.util

import android.util.Log
import kotlin.reflect.KProperty

/**
 * Convenience class to create logs
 */
class Log(private val tag: String) {
    companion object {
        operator fun getValue(requestBuilder: Any, property: KProperty<*>) = Log(requestBuilder.javaClass.name)
    }

    fun debug(msg: String, throwable: Throwable? = null) =
        Log.d(tag, msg, throwable)


    fun error(msg: String, throwable: Throwable? = null) {
        Log.e(tag, msg, throwable)
    }

    fun info(msg: String, throwable: Throwable? = null) {
        Log.i(tag, msg, throwable)
    }
}