package de.taz.app.android.sentry

/**
 * Non Free Variant implementation of the SentryWrapperFactoryInterface.
 * It is using the [DefaultSentryWrapper] which is forwarding to the Sentry instance.
 */
class SentryWrapperFactory : SentryWrapperFactoryInterface {
    override fun createInstance(): SentryWrapperInterface = DefaultSentryWrapper()
}