package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
enum class CustomerType {
    digital,
    combo,
    sample,
    promotion,
    deliveryBreaker,
    revocation,
    employees,
    demo,
    unknown
}