package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class CancellationStatusDto(
    val tazIdMail: String? = null,
    val cancellationLink: String? = null,
    val canceled: Boolean,
    val info: CancellationInfoDto,
)