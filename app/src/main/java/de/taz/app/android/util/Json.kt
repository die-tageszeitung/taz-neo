package de.taz.app.android.util

import de.taz.app.android.api.variables.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

private val module = SerializersModule {
    polymorphic(Variables::class) {
        subclass(AuthenticationVariables::class)
        subclass(CancellationVariables::class)
        subclass(CheckSubscriptionIdVariables::class)
        subclass(DownloadStartVariables::class)
        subclass(DownloadStopVariables::class)
        subclass(ErrorReportVariables::class)
        subclass(FeedVariables::class)
        subclass(IssueVariables::class)
        subclass(NotificationVariables::class)
        subclass(PasswordResetVariables::class)
        subclass(SearchVariables::class)
        subclass(SubscriptionId2TazIdVariables::class)
        subclass(SubscriptionPollVariables::class)
        subclass(SubscriptionResetVariables::class)
        subclass(SubscriptionVariables::class)
        subclass(TrialSubscriptionVariables::class)
    }
}

val Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    allowSpecialFloatingPointValues = true
    allowStructuredMapKeys = true
    prettyPrint = false
    useArrayPolymorphism = false

    serializersModule = module
}