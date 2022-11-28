package de.taz.app.android.api.models

data class CancellationStatus(
    val tazIdMail: String? = null,
    val cancellationLink: String? = null,
    val canceled: Boolean,
    val info: CancellationInfo,
)

enum class CancellationInfo {
    aboId,
    tazId,
    noAuthToken,
    elapsed,
    specialAccess,
    UNKNOWN_RESPONSE,
}