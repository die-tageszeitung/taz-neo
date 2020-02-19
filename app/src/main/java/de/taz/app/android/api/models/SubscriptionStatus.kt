package de.taz.app.android.api.models

enum class SubscriptionStatus {
    valid,
    tazIdNotValid,
    aboIdNotValid,
    elapsed,
    invalidConnection,
    invalidMail,
    alreadyLinked,
    waitForMail,
    waitForProc,
    noPollEntry
}