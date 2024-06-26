package de.taz.app.android.api.variables

import android.os.Environment
import android.os.StatFs
import de.taz.app.android.BuildConfig
import kotlinx.serialization.Serializable

@Serializable
data class ErrorReportVariables(
    val installationId: String,
    val deviceFormat: DeviceFormat,
    val pushToken: String? = null,
    val eMail: String? = null,
    val message: String? = null,
    val lastAction: String? = null,
    val conditions: String? = null,
    val storageType: String? = null,
    val errorProtocol: String? = null,
    val deviceOS: String? = System.getProperty("os.version"),
    val appVersion: String = "${BuildConfig.VERSION_NAME} ${
        if (BuildConfig.IS_LMD) {
            "(LMd)"
        } else {
            "(taz)"
        }
    }",
    val storageAvailable: String? = "${StatFs(Environment.getDataDirectory().path).availableBytes} Bytes",
    val storageUsed: String? = "${StatFs(Environment.getDataDirectory().path).totalBytes - StatFs(Environment.getDataDirectory().path).availableBytes} Bytes",
    val ramAvailable: String?,
    val ramUsed: String?,
    val architecture: String? = android.os.Build.SUPPORTED_ABIS.joinToString(", "),
    val deviceType: DeviceType = DeviceType.android,
    val deviceName: String = android.os.Build.MODEL,
    val deviceVersion: String? = "${android.os.Build.VERSION.SDK_INT} (Android ${android.os.Build.VERSION.RELEASE})",
    val screenshotName: String? = null,
    val screenshot: String? = null
): Variables
