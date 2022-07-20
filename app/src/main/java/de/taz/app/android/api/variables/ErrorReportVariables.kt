package de.taz.app.android.api.variables

import android.os.Environment
import android.os.StatFs
import kotlinx.serialization.Serializable
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import kotlinx.serialization.Required
import kotlinx.serialization.encodeToString
import de.taz.app.android.util.Json

@Serializable
data class ErrorReportVariables(
    @Required val installationId: String,
    @Required val deviceFormat: DeviceFormat,
    val pushToken: String? = null,
    val eMail: String? = null,
    val message: String? = null,
    val lastAction: String? = null,
    val conditions: String? = null,
    val storageType: String? = null,
    val errorProtocol: String? = null,
    @Required val deviceOS: String? = System.getProperty("os.version"),
    @Required val appVersion: String = BuildConfig.VERSION_NAME,
    @Required val storageAvailable: String? = "${StatFs(Environment.getDataDirectory().path).availableBytes} Bytes",
    @Required val storageUsed: String? = "${StatFs(Environment.getDataDirectory().path).totalBytes - StatFs(Environment.getDataDirectory().path).availableBytes} Bytes",
    val ramAvailable: String?,
    val ramUsed: String?,
    @Required val architecture: String? = android.os.Build.SUPPORTED_ABIS.joinToString (", "),
    @Required val deviceType: DeviceType = DeviceType.android,
    @Required val deviceName: String = android.os.Build.MODEL,
    @Required val deviceVersion: String? = "${android.os.Build.VERSION.SDK_INT} (Android ${android.os.Build.VERSION.RELEASE})",
    val screenshotName: String? = null,
    val screenshot: String? = null
): Variables
