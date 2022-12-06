package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = SubscriptionStatusDtoEnumSerializer::class)
enum class SubscriptionStatusDto {
    alreadyLinked,
    elapsed,
    ibanNoIban,
    ibanInvalidChecksum,
    ibanNoSepaCountry,
    invalidAccountHolder,
    invalidCity,
    invalidConnection,
    invalidCountry,
    invalidFirstName,
    invalidMail,
    invalidPostcode,
    invalidSurname,
    invalidStreet,
    nameTooLong,
    noFirstName,
    noPollEntry,
    noSurname,
    priceNotValid,
    subscriptionIdNotValid,
    tazIdNotValid,
    tooManyPollTries,
    valid,
    waitForMail,
    waitForProc,
    UNKNOWN
}

object SubscriptionStatusDtoEnumSerializer : EnumSerializer<SubscriptionStatusDto>(
    SubscriptionStatusDto.values(),
    SubscriptionStatusDto.UNKNOWN
)