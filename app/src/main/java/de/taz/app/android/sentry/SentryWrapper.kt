package de.taz.app.android.sentry

import android.content.Context

object SentryWrapper : SentryWrapperInterface {
    private var instance: SentryWrapperInterface? = null

    private fun getWrapperInstance(): SentryWrapperInterface {
        var currentInstance = instance
        if (currentInstance == null) {
            val factory = SentryWrapperFactory()
            currentInstance = factory.createInstance()
            instance = currentInstance
        }
        return currentInstance
    }

    override fun init(context: Context) {
        getWrapperInstance().init(context)
    }

    override fun setUser(userId: String) {
        getWrapperInstance().setUser(userId)
    }

    override fun captureMessage(message: String) {
        getWrapperInstance().captureMessage(message)
    }

    override fun captureMessage(message: String, level: SentryWrapperLevel) {
        getWrapperInstance().captureMessage(message, level)
    }

    override fun captureException(throwable: Throwable) {
        getWrapperInstance().captureException(throwable)
    }

    override fun addBreadcrumb(message: String) {
        getWrapperInstance().addBreadcrumb(message)
    }
}
