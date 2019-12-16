package de.taz.app.android.util

import android.util.Log
import io.sentry.Sentry
import io.sentry.event.BreadcrumbBuilder
import kotlin.reflect.KProperty

/**
 * Convenience class to create logs
 * messages will be stored as breadcrumbs for sentry
 * if a throwable is given it is handed to sentry
 */
open class Log(private val tag: String) {
    companion object {
        operator fun getValue(requestBuilder: Any, property: KProperty<*>) = Log(requestBuilder.javaClass.name)
    }

    open fun debug(message: String, throwable: Throwable? = null) {
        Log.d(tag, message, throwable)
        setSentryBreadcrump(message, throwable)
    }


    open fun error(message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        setSentryBreadcrump(message, throwable)
    }

    open fun info(message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
        setSentryBreadcrump(message, throwable)
    }

    open fun warn(message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        setSentryBreadcrump(message, throwable)
    }

    private fun setSentryBreadcrump(message: String, throwable: Throwable?) {
        Sentry.getContext().recordBreadcrumb(
            BreadcrumbBuilder().setMessage("$tag: $message").build()
        )
        throwable?.let { Sentry.capture(throwable) }
    }

}