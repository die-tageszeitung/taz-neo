package de.taz.app.android.util

import android.util.Log
import de.taz.app.android.annotation.Mockable
import io.sentry.Sentry
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.KProperty

private const val LOG_TRACE_LENGTH = 100

/**
 * Convenience class to create logs
 * messages will be stored as breadcrumbs for sentry
 * if a throwable is given it is handed to sentry
 */
@Mockable
class Log(private val tag: String) {
    companion object {
        operator fun getValue(requestBuilder: Any, property: KProperty<*>) =
            Log(requestBuilder::class.java.name)

        var trace: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())
    }


    fun verbose(message: String, throwable: Throwable? = null) {
        Log.v(tag, message, throwable)
        setSentryBreadcrumb(message, throwable)
        addToTrace(message)
    }


    fun debug(message: String, throwable: Throwable? = null) {
        Log.d(tag, message, throwable)
        setSentryBreadcrumb(message, throwable)
        addToTrace(message)
    }


    fun error(message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        setSentryBreadcrumb(message, throwable)
        addToTrace(message)
    }

    fun info(message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
        setSentryBreadcrumb(message, throwable)
        addToTrace(message)
    }

    fun warn(message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        setSentryBreadcrumb(message, throwable)
        addToTrace(message)
    }

    private fun setSentryBreadcrumb(message: String, throwable: Throwable?) {
        Sentry.addBreadcrumb("$tag: $message")
        throwable?.let { Sentry.captureException(throwable) }
    }

    /**
    keep [LOG_TRACE_LENGTH] lines of logs for attach to error reports;
    if a log line is longer than 200 chars, truncate it
     */
    private fun addToTrace(message: String) {
        val truncateMessage = if (message.length > 200) message.substring(0, 200) else message

        val time = SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS", Locale.GERMAN).format(
            Calendar.getInstance().time
        )

        val traceLine = "$time $tag: $truncateMessage\n"
        if (trace.size < LOG_TRACE_LENGTH) {
            trace.add(traceLine)
        } else {
            trace.removeAt(0)
            trace.add(traceLine)
        }
    }

}