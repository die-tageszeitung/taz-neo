package de.taz.app.android

import android.content.Context
import io.sentry.android.core.SentryAndroid

object SentryProvider {
    fun initSentry(context: Context) {
        SentryAndroid.init(context) { options ->
            options.environment = BuildConfig.SENTRY_ENVIRONMENT
        }

    }
}