package de.taz.app.android.api.mappers

import android.util.Log
import de.taz.app.android.api.dto.CustomerTypeDto
import de.taz.app.android.api.dto.CustomerTypeDto.*
import de.taz.app.android.api.models.CustomerType
import io.sentry.Sentry

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
                Log.w(CustomerTypeMapper::class.java.name, hint)
                Sentry.captureMessage(hint)
                CustomerType.unknown
            }
        }
}