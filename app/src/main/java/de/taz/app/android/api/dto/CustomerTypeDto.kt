package de.taz.app.android.api.dto

import de.taz.app.android.api.EnumSerializer
import kotlinx.serialization.Serializable

@Serializable(with = CustomerTypeDtoEnumSerializer::class)
enum class CustomerTypeDto {
    digital,
    combo,
    sample,
    promotion,
    deliveryBreaker,
    revocation,
    employees,
    demo,
    UNKNOWN
}

object CustomerTypeDtoEnumSerializer :
    EnumSerializer<CustomerTypeDto>(CustomerTypeDto.values(), CustomerTypeDto.UNKNOWN)