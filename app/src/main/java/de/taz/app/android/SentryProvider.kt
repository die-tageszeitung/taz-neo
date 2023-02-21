package de.taz.app.android

import android.content.Context
import io.sentry.android.core.SentryAndroid

object SentryProvider {
    fun initSentry(context: Context) {
        SentryAndroid.init(context) { options ->
            // We will be using one sentry project for both apps for now.
            options.dsn = "https://796a4b1c8b794a57a9f1b9e6dc7331a8@sentry.taz.de/2"
            // To distinguish bugs between lmd and taz we set the appVariant tag on all sentry events
            options.setTag("appVariant", BuildConfig.FLAVOR_owner)
            options.environment = BuildConfig.SENTRY_ENVIRONMENT
        }
    }
}