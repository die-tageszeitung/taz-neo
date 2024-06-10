package de.taz.app.android.sentry

/**
 * This interface has to be implemented by all build variant dependent TrackerFactorys.
 */
interface SentryWrapperFactoryInterface {
    fun createInstance(): SentryWrapperInterface
}