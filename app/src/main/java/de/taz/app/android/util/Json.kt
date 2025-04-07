package de.taz.app.android.util

import de.taz.app.android.api.variables.AppVariables
import de.taz.app.android.api.variables.AuthenticationVariables
import de.taz.app.android.api.variables.CancellationVariables
import de.taz.app.android.api.variables.CheckSubscriptionIdVariables
import de.taz.app.android.api.variables.DownloadStartVariables
import de.taz.app.android.api.variables.DownloadStopVariables
import de.taz.app.android.api.variables.ErrorReportVariables
import de.taz.app.android.api.variables.FeedVariables
import de.taz.app.android.api.variables.GetCustomerDataVariables
import de.taz.app.android.api.variables.IssueVariables
import de.taz.app.android.api.variables.NotificationVariables
import de.taz.app.android.api.variables.PasswordResetVariables
import de.taz.app.android.api.variables.SaveCustomerDataVariables
import de.taz.app.android.api.variables.SearchVariables
import de.taz.app.android.api.variables.SubscriptionFormDataVariables
import de.taz.app.android.api.variables.SubscriptionId2TazIdVariables
import de.taz.app.android.api.variables.SubscriptionPollVariables
import de.taz.app.android.api.variables.SubscriptionResetVariables
import de.taz.app.android.api.variables.SubscriptionVariables
import de.taz.app.android.api.variables.TrialSubscriptionVariables
import de.taz.app.android.api.variables.Variables
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

// we need to define all subclasses of [Variable] here so kotlinx serialization can correctly
// de- and encode the classes
private val module = SerializersModule {
    polymorphic(Variables::class) {
        subclass(AuthenticationVariables::class)
        subclass(AppVariables::class)
        subclass(CancellationVariables::class)
        subclass(CheckSubscriptionIdVariables::class)
        subclass(DownloadStartVariables::class)
        subclass(DownloadStopVariables::class)
        subclass(ErrorReportVariables::class)
        subclass(FeedVariables::class)
        subclass(GetCustomerDataVariables::class)
        subclass(IssueVariables::class)
        subclass(NotificationVariables::class)
        subclass(PasswordResetVariables::class)
        subclass(SaveCustomerDataVariables::class)
        subclass(SearchVariables::class)
        subclass(SubscriptionFormDataVariables::class)
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

    //    Set the class discriminator (the json field used to store the class name for
    //    for polymorphic types - in our case only Variables). The default is "type" which clashes
    //    with AppVariables. There is a @JsonClassDiscriminator annotation that could be applied to
    //    the variables abstract class, but it seems it is only used for deserialization.
    classDiscriminator = "_clstype"
}