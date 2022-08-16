package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
enum class SubscriptionType {
    regular,
    special,
    unknown
}