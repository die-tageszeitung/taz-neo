package de.taz.app.android.api.variables

import android.app.ActivityManager
import android.os.Environment
import android.os.StatFs
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.singletons.AuthHelper

@JsonClass(generateAdapter = true)
data class ErrorReportVariables(
    val eMail: String? = null,
    val message: String? = null,
    val lastAction: String? = null,
    val conditions: String? = null,
    val storageType: String? = null,
    val errorProtocol: String? = null,
    val deviceName: String? = android.os.Build.MODEL,
    val deviceVersion: String? = android.os.Build.VERSION.RELEASE,
    val deviceOS: String? = System.getProperty("os.version"),
    val appVersion: String = BuildConfig.VERSION_NAME,
    val installationId: String = AuthHelper.getInstance().installationId,
    val storageAvailable: String? = "${StatFs(Environment.getDataDirectory().path).availableBytes} Bytes",
    val storageUsed: String? = "${StatFs(Environment.getDataDirectory().path).totalBytes - StatFs(Environment.getDataDirectory().path).availableBytes} Bytes",
    val ramAvailable: String? = ActivityManager.MemoryInfo().availMem.toString(),
    val ramUsed: String? = (ActivityManager.MemoryInfo().totalMem - ActivityManager.MemoryInfo().availMem).toString(),
    val pushToken: String? = FirebaseHelper.getInstance().firebaseToken ?: "",
    val architecture: String? = android.os.Build.SUPPORTED_ABIS.joinToString (", "),
    val deviceType: DeviceType = DeviceType.android,
    val deviceFormat: DeviceFormat = DeviceFormat.mobile
): Variables {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(ErrorReportVariables::class.java)

        return adapter.toJson(this)
    }
}
