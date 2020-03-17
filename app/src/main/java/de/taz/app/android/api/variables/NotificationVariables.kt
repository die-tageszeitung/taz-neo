package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.singletons.JsonHelper

@JsonClass(generateAdapter = true)
data class NotificationVariables(
    val pushToken: String,
    val oldToken: String? = null,
    val deviceMessageSound: String? = null,
    val textNotification: Boolean = true,
    val deviceType: DeviceType = DeviceType.android,
    val deviceFormat: DeviceFormat = DeviceFormat.mobile
): Variables {
    override fun toJson(): String = JsonHelper.toJson(this)
}
