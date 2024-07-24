package de.taz.app.android.sentry

import android.content.Context

interface SentryWrapperInterface {
    fun init(context: Context)
    fun setUser(userId: String)
    fun captureMessage(message: String)
    fun captureMessage(message: String, level: SentryWrapperLevel)
    fun captureException(throwable: Throwable)
    fun addBreadcrumb(message: String)
    fun addLogcatBreadcrumb(tag: String, level: SentryWrapperLevel, message: String, throwable: Throwable?)
}