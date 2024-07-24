package de.taz.app.android.sentry

import android.content.Context

class NoOpSentryWrapper: SentryWrapperInterface {
    override fun init(context: Context) {}
    override fun setUser(userId: String) {}
    override fun captureMessage(message: String) {}
    override fun captureMessage(message: String, level: SentryWrapperLevel) {}
    override fun captureException(throwable: Throwable) {}
    override fun addBreadcrumb(message: String) {}
    override fun addLogcatBreadcrumb(tag: String, level: SentryWrapperLevel, message: String, throwable: Throwable?) {}
}