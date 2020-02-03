package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.util.AuthHelper

@JsonClass(generateAdapter = true)
data class ErrorReportVariables(
    val message: String? = null,
    val lastAction: String? = null,
    val conditions: String? = null,
    val errorProtocol: String? = null,
    val errorCategory: String? = null,
    val eMail: String? = null,
    val deviceName: String? = null,
    val deviceVersion: String? = null,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val installationId: String = AuthHelper.getInstance().installationId,
    val storageAvailable: String? = null,
    val storageUsed: String? = null,
    val storageType: String? = null,
    val ramAvailable: String? = null,
    val ramUsed: String? = null,
    val pushToken: String? = FirebaseHelper.getInstance().firebaseToken ?: "",
    val architecture: String? = null,
    val deviceType: DeviceType = DeviceType.android,
    val deviceFormat: DeviceFormat = DeviceFormat.mobile
): Variables {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(ErrorReportVariables::class.java)

        return adapter.toJson(this)
    }
}
