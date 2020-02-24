package de.taz.app.android.util

import android.util.Log
import de.taz.app.android.annotation.Mockable
import io.sentry.Sentry
import io.sentry.event.BreadcrumbBuilder
import kotlin.reflect.KProperty

/**
 * Convenience class to create logs
 * messages will be stored as breadcrumbs for sentry
 * if a throwable is given it is handed to sentry
 */
@Mockable
class Log(private val tag: String) {
    companion object {
        operator fun getValue(requestBuilder: Any, property: KProperty<*>) =
            Log(requestBuilder.javaClass.name)
        var trace = mutableListOf<String>()
    }

    fun debug(message: String, throwable: Throwable? = null) {
        Log.d(tag, message, throwable)
        setSentryBreadcrump(message, throwable)
        addToTrace(message)
    }


    fun error(message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        setSentryBreadcrump(message, throwable)
        addToTrace(message)
    }

    fun info(message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
        setSentryBreadcrump(message, throwable)
    }

    fun warn(message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        setSentryBreadcrump(message, throwable)
        addToTrace(message)
    }

    private fun setSentryBreadcrump(message: String, throwable: Throwable?) {
        Sentry.getContext().recordBreadcrumb(
            BreadcrumbBuilder().setMessage("$tag: $message").build()
        )
        throwable?.let { Sentry.capture(throwable) }
    }

    /**
        keep 50 lines of logs for attach to error reports
     */
    private fun addToTrace(message: String) {
        val traceLine = "$tag: $message\n"
        if (trace.size < 50) {
          trace.add(traceLine)
        }
        else {
            trace.removeAt(0)
            trace.add(traceLine)
        }
    }

}