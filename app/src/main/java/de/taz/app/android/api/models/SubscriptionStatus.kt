package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = false)
enum class SubscriptionStatus {
    alreadyLinked,
    elapsed,
    ibanNoIban,
    ibanInvalidChecksum,
    ibanNoSepaCountry,
    invalidAccountHolder,
    invalidConnection,
    invalidFirstName,
    invalidMail,
    invalidSurname,
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