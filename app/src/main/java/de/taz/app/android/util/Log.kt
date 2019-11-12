package de.taz.app.android.util

import android.util.Log
import kotlin.reflect.KProperty

/**
 * Convenience class to create logs
 */
open class Log(private val tag: String) {
    companion object {
        operator fun getValue(requestBuilder: Any, property: KProperty<*>) = Log(requestBuilder.javaClass.name)
    }

    open fun debug(msg: String, throwable: Throwable? = null) =
        Log.d(tag, msg, throwable)


    open fun error(msg: String, throwable: Throwable? = null) {
        Log.e(tag, msg, throwable)
    }

    open fun info(msg: String, throwable: Throwable? = null) {
        Log.i(tag, msg, throwable)
    }

    open fun warn(msg: String, throwable: Throwable? = null) {
        Log.w(tag, msg, throwable)
    }
}