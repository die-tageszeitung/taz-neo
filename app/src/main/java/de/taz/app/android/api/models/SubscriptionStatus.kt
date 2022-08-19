package de.taz.app.android.api.models

import kotlinx.serialization.Serializable


@Serializable
enum class SubscriptionStatus {
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
    toManyPollTrys,
    valid,
    waitForMail,
    waitForProc
}