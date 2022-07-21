package de.taz.app.android.api.models

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CancellationStatus(
    val tazIdMail: String? = null,
    val cancellationLink: String? = null,
    val canceled: Boolean,
    val info: CancellationInfo,
)

@JsonClass(generateAdapter = false)
enum class CancellationInfo {
    aboId,
    tazId,
    noAuthToken,
    elapsed,
    specialAccess,
}