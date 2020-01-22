package de.taz.app.android.api.models

enum class AboStatus {
    valid,
    tazIdNotValid,
    aboIdNotValid,
    elapsed,
    invalidConnection,
    alreadyLinked,
    waitForEmail,
    waitForProc,
    noPollEntry
}