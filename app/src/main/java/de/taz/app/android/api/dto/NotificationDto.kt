package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val perform: PerformDto? = null
)

@Serializable
enum class PerformDto {
    subscriptionPoll
}