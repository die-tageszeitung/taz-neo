package de.taz.app.android.api.dto


data class Notification(
    val pushToken: String,
    val textNotification: Boolean,
    val deviceFormat: DeviceFormat,
    val deviceType: DeviceType = DeviceType.android,
    val deviceMessageSound: String?
)