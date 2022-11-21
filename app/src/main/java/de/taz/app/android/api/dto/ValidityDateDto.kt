package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
 class ValidityDateDto(
    val date: String,
    val validityDate: String? = null
)