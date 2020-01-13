package de.taz.app.android.api.models

enum class AboId2TazIdStatus {
    valid,
    tazIdNotValid,
    aboIdNotValid,
    elapsed,
    invalidConnection,
    alreadyLinked,
    wait
}