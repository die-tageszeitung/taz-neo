package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = SubscriptionFormDataErrorDtoEnumSerializer::class)
enum class SubscriptionFormDataErrorDto {
    noMail,
    invalidMail,
    noSurname,
    noFirstName,
    noCity,
    UNKNOWN
}

object SubscriptionFormDataErrorDtoEnumSerializer : EnumSerializer<SubscriptionFormDataErrorDto>(
    SubscriptionFormDataErrorDto.values(),
    SubscriptionFormDataErrorDto.UNKNOWN
)