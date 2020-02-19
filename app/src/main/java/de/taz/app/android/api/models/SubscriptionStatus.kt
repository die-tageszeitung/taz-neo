package de.taz.app.android.api.models

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