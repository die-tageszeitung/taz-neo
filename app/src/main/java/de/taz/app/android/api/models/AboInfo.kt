package de.taz.app.android.api.models

data class AboInfo(
    val status: AboStatus,
    val message: String?,
    val token: String?
)
