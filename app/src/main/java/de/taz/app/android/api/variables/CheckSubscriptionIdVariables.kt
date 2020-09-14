package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.singletons.JsonHelper

@JsonClass(generateAdapter = true)
data class CheckSubscriptionIdVariables(
    val subscriptionId: Int,
    val password: String,
    val deviceName: String? = android.os.Build.MODEL,
    val deviceVersion: String? = android.os.Build.VERSION.RELEASE,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val deviceFormat: DeviceFormat = DeviceFormat.mobile,
    val deviceType: DeviceType = DeviceType.android,
    val deviceOS: String? = System.getProperty("os.version"),
) : Variables {
    override fun toJson() = JsonHelper.toJson(this)
}

