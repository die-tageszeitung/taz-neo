package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable

@Serializable
enum class SubscriptionFormDataType {
    expiredDigiPrint,
    trialSubscription,
    expiredDigiSubscription,
    print2Digi,
    printPlusDigi,
    weekendPlusDigi,
}