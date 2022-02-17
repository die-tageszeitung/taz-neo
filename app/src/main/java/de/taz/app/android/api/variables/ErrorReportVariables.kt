package de.taz.app.android.api.variables

import android.os.Environment
import android.os.StatFs
import com.squareup.moshi.JsonClass
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.singletons.JsonHelper

@JsonClass(generateAdapter = true)
data class ErrorReportVariables(
    val installationId: String,
    val pushToken: String? = null,
    val eMail: String? = null,
    val message: String? = null,
    val lastAction: String? = null,
    val conditions: String? = null,
    val storageType: String? = null,
    val errorProtocol: String? = null,
    val deviceOS: String? = System.getProperty("os.version"),
    val appVersion: String = BuildConfig.VERSION_NAME,
    val storageAvailable: String? = "${StatFs(Environment.getDataDirectory().path).availableBytes} Bytes",
    val storageUsed: String? = "${StatFs(Environment.getDataDirectory().path).totalBytes - StatFs(Environment.getDataDirectory().path).availableBytes} Bytes",
    val ramAvailable: String?,
    val ramUsed: String?,
    val deviceFormat: DeviceFormat,
    val architecture: String? = android.os.Build.SUPPORTED_ABIS.joinToString (", "),
    val deviceType: DeviceType = DeviceType.android,
    val deviceName: String = android.os.Build.MODEL,
    val deviceVersion: String? = "${android.os.Build.VERSION.SDK_INT} (Android ${android.os.Build.VERSION.RELEASE})",
    val screenshotName: String? = null,
    val screenshot: String? = null
): Variables {
    override fun toJson(): String = JsonHelper.toJson(this)
}

