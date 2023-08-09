package de.taz.app.android.api.mappers

import de.taz.app.android.api.dto.CustomerTypeDto
import de.taz.app.android.api.dto.CustomerTypeDto.UNKNOWN
import de.taz.app.android.api.dto.CustomerTypeDto.combo
import de.taz.app.android.api.dto.CustomerTypeDto.deliveryBreaker
import de.taz.app.android.api.dto.CustomerTypeDto.demo
import de.taz.app.android.api.dto.CustomerTypeDto.digital
import de.taz.app.android.api.dto.CustomerTypeDto.employees
import de.taz.app.android.api.dto.CustomerTypeDto.promotion
import de.taz.app.android.api.dto.CustomerTypeDto.revocation
import de.taz.app.android.api.dto.CustomerTypeDto.sample
import de.taz.app.android.api.models.CustomerType
import de.taz.app.android.util.Log

object CustomerTypeMapper {
    fun from(customerTypeDto: CustomerTypeDto): CustomerType =
        when(customerTypeDto) {
            digital -> CustomerType.digital
            combo -> CustomerType.combo
            sample -> CustomerType.sample
            promotion -> CustomerType.promotion
            deliveryBreaker -> CustomerType.deliveryBreaker
            revocation -> CustomerType.revocation
            employees -> CustomerType.employees
            demo -> CustomerType.demo
            UNKNOWN -> {
                val hint = "Encountered UNKNOWN CustomerTypeDtp, falling back to CustomerType.unknown"
                Log(this::class.java.name).warn(hint)
                CustomerType.unknown
            }
        }
}