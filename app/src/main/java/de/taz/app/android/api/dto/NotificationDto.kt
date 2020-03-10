package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NotificationDto(
    val perform: PerformDto? = null
)

enum class PerformDto {
    subscriptionPoll
}