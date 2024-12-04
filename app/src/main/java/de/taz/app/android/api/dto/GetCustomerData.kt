package de.taz.app.android.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class GetCustomerData(
    val error: String? = null,
    val customerDataList: List<CustomerDataDto>? = null,
    val authInfo: AuthInfoDto,
)