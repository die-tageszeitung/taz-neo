package de.taz.app.android.util

import de.taz.app.android.api.variables.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

// we need to define all subclasses of [Variable] here so kotlinx serialization can correctly
// de- and encode the classes
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

// our Json configuration
val Json = Json {
    // we do not want the app to crash if a property exists we request but ignore
    ignoreUnknownKeys = true
    // encode values to and from Json even if they match the default value
    encodeDefaults = true

    isLenient = true
    allowSpecialFloatingPointValues = true
    allowStructuredMapKeys = true
    prettyPrint = false
    useArrayPolymorphism = false

    // register our serializerModule
    serializersModule = module
}