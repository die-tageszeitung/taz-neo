package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.firebase.FirebaseHelper

@JsonClass(generateAdapter = true)
data class NotificationVariables(
    val pushToken: String = FirebaseHelper.getInstance().firebaseToken ?: "",
    val deviceMessageSound: String? = null,
    val textNotification: Boolean = true,
    val deviceType: DeviceType = DeviceType.android,
    val deviceFormat: DeviceFormat = DeviceFormat.mobile
): Variables {

    override fun toJson(): String {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(NotificationVariables::class.java)

        return adapter.toJson(this)
    }
}