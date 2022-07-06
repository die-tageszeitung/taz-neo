package de.taz.app.android.api.variables

import kotlinx.serialization.Serializable
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import kotlinx.serialization.Required
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class DownloadStartVariables(
    @Required val feedName: String,
    @Required val issueDate: String,
    @Required val isAutomatically: Boolean,
    @Required val installationId: String,
    @Required val isPush: Boolean,
    @Required val deviceFormat: DeviceFormat,
    @Required val deviceName: String = android.os.Build.MODEL,
    @Required val deviceVersion: String = android.os.Build.VERSION.RELEASE,
    @Required val appVersion: String = BuildConfig.VERSION_NAME,
    @Required val deviceType: DeviceType = DeviceType.android,
    @Required val deviceOS: String? = System.getProperty("os.version"),
    @Required val pushToken: String?,
    @Required val deviceMessageSound: String? = null,
    @Required val textNotification: Boolean = true
) : Variables {
    override fun toJson() = Json.encodeToString(this)
}
