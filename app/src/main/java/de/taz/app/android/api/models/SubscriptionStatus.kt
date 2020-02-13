package de.taz.app.android.api.models

enum class SubscriptionStatus {
    valid,
    tazIdNotValid,
    aboIdNotValid,
    elapsed,
    invalidConnection,
    alreadyLinked,
    waitForMail,
    waitForProc,
    noPollEntry
}