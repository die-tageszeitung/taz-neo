package de.taz.app.android.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
enum class DeviceFormat { mobile, tablet, desktop }
