package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val pushToken: String,
    val textNotification: Boolean,
    val deviceFormat: DeviceFormat,
    val deviceType: DeviceType = DeviceType.android,
    val deviceMessageSound: String?
)