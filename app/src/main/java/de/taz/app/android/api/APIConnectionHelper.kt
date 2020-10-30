package de.taz.app.android.api

import de.taz.app.android.data.ConnectionHelper


class APIConnectionHelper(private val graphQlClient: GraphQlClient): ConnectionHelper() {
    override suspend fun checkConnectivity(): Boolean {
        return try {
            transformToConnectivityException {
                val wrapperDto = graphQlClient.query(QueryType.AppInfo)
                wrapperDto.errors.isEmpty()
            }
        } catch (e: ConnectivityException.Recoverable) {
            false
        }
    }
}