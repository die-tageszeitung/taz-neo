package de.taz.app.android.api.models

import kotlinx.serialization.Serializable

@Serializable
enum class SubscriptionFormDataError {
    noMail,
    invalidMail,
    noSurname,
    noFirstName,
    noCity,
}