package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import de.taz.app.android.firebase.FirebaseHelper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.JsonHelper

@JsonClass(generateAdapter = true)
data class DownloadStartVariables(
    val feedName: String,
    val issueDate: String,
    val deviceName: String = android.os.Build.MODEL,
    val deviceVersion: String = android.os.Build.VERSION.RELEASE,
    val appVersion: String = BuildConfig.VERSION_NAME,
    val isPush: Boolean = FirebaseHelper.getInstance().isPush,
    val installationId: String = AuthHelper.getInstance().installationId,
    val deviceFormat: DeviceFormat = DeviceFormat.mobile,
    val deviceType: DeviceType = DeviceType.android
) : Variables {
    override fun toJson() = JsonHelper.toJson(this)
}
