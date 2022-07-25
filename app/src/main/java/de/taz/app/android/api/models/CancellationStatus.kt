package de.taz.app.android.api.models

import kotlinx.serialization.Serializable

@Serializable
data class CancellationStatus(
    val tazIdMail: String? = null,
    val cancellationLink: String? = null,
    val canceled: Boolean,
    val info: CancellationInfo,
)

@Serializable
enum class CancellationInfo {
    aboId,
    tazId,
    noAuthToken,
    elapsed,
    specialAccess,
}