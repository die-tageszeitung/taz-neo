package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = SubscriptionTypeDtoEnumSerializer::class)
enum class SubscriptionTypeDto {
    regular,
    special,
    UNKNOWN
}

object SubscriptionTypeDtoEnumSerializer :
    EnumSerializer<SubscriptionTypeDto>(SubscriptionTypeDto.values(), SubscriptionTypeDto.UNKNOWN)