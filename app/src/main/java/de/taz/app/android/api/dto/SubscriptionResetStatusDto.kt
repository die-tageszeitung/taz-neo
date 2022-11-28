package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = SubscriptionResetStatusDtoEnumSerializer::class)
enum class SubscriptionResetStatusDto {
    ok,
    invalidSubscriptionId,
    noMail,
    invalidConnection,
    UNKNOWN
}

object SubscriptionResetStatusDtoEnumSerializer : EnumSerializer<SubscriptionResetStatusDto>(
    SubscriptionResetStatusDto.values(),
    SubscriptionResetStatusDto.UNKNOWN
)