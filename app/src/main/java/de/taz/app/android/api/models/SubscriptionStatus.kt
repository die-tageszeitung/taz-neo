package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = false)
enum class SubscriptionStatus {
    valid,
    tazIdNotValid,
    elapsed,
    invalidConnection,
    invalidMail,
    subscriptionIdNotValid,
    alreadyLinked,
    waitForMail,
    waitForProc,
    noPollEntry
}