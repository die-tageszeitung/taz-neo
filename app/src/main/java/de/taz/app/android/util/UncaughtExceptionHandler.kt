package de.taz.app.android.util

import android.content.Context
import android.widget.Toast
import de.taz.app.android.R
import de.taz.app.android.api.mappers.MappingException
import io.sentry.Sentry
import kotlin.system.exitProcess

class UncaughtExceptionHandler(private val applicationContext: Context) :
    Thread.UncaughtExceptionHandler {

    private val log by Log

    private val rootHandler = Thread.getDefaultUncaughtExceptionHandler()

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        when {
            isMappingException(e) -> handleMappingException(e)
            else -> rootHandler?.uncaughtException(t, e)
        }
    }

    private fun isMappingException(e: Throwable): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is MappingException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun handleMappingException(e: Throwable) {
        val hint = "Could not map a response from the API. User probably needs to update"
        log.error(hint, e)
        Sentry.captureException(e, hint)
        Toast.makeText(
            applicationContext, R.string.toast_error_mapping_update, Toast.LENGTH_LONG
        ).show()
        // We wont let the app crash but try to exit with a status 0 to indicate normal termination
        exitProcess(0)
    }
}