package de.taz.app.android.api.variables

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.taz.app.android.api.dto.DeviceFormat
import de.taz.app.android.api.dto.DeviceType
import java.util.*

@JsonClass(generateAdapter = true)
data class DownloadStartVariables(
        val feedName: String,
        val issueDate: String,
        val deviceName: String = "TODO",
        val deviceVersion: String = "TODO",
        val appVersion: String = "TODO",
        val isPush: Boolean = false, // TODO
        val installationID: String = UUID.randomUUID().toString(), // TODO
        val deviceFormat: DeviceFormat = DeviceFormat.mobile,
        val deviceType: DeviceType = DeviceType.android
) : Variables {

        override fun toJson(): String {
                val moshi = Moshi.Builder().build()
                val adapter = moshi.adapter(DownloadStartVariables::class.java)

                return adapter.toJson(this)
        }
}