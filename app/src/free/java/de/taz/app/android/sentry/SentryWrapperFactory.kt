package de.taz.app.android.sentry

/**
 * Free Variant implementation of the SentryWrapperFactoryInterface.
 * It will always use the No Operation fake Sentry wrapper.
 */
class SentryWrapperFactory : SentryWrapperFactoryInterface {
    override fun createInstance(): SentryWrapperInterface = NoOpSentryWrapper()
}