package de.taz.app.android.sentry

import android.content.Context
import de.taz.app.android.BuildConfig
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.protocol.User
import io.sentry.android.core.SentryAndroid

/**
 * Default Sentry Wrapper forwarding all calls to the Sentry instance.
 */
class DefaultSentryWrapper : SentryWrapperInterface {
    override fun init(context: Context) {
        SentryAndroid.init(context) { options ->
            // We will be using one sentry project for both apps for now.
            options.dsn = "https://796a4b1c8b794a57a9f1b9e6dc7331a8@sentry.taz.de/2"
            // To distinguish bugs between lmd and taz we set the appVariant tag on all sentry events
            options.setTag("appVariant", BuildConfig.FLAVOR_owner)
            options.environment = BuildConfig.SENTRY_ENVIRONMENT
        }
    }

    override fun setUser(userId: String) {
        val user = User().apply {
            id = userId
        }
        Sentry.setUser(user)
    }

    override fun captureMessage(message: String) {
        Sentry.captureMessage(message)
    }

    override fun captureMessage(message: String, level: SentryWrapperLevel) {
        val sentryLevel = when (level) {
            SentryWrapperLevel.DEBUG -> SentryLevel.DEBUG
            SentryWrapperLevel.INFO -> SentryLevel.INFO
            SentryWrapperLevel.WARNING -> SentryLevel.WARNING
            SentryWrapperLevel.ERROR -> SentryLevel.ERROR
            SentryWrapperLevel.FATAL -> SentryLevel.FATAL
        }
        Sentry.captureMessage(message, sentryLevel)
    }

    override fun captureException(throwable: Throwable) {
        Sentry.captureException(throwable)
    }

    override fun addBreadcrumb(message: String) {
        Sentry.addBreadcrumb(message)
    }
}