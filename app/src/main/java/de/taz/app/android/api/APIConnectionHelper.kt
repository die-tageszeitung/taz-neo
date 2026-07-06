package de.taz.app.android.api

import de.taz.app.android.data.ConnectionHelper


class APIConnectionHelper(private val graphQlClient: GraphQlClient): ConnectionHelper() {
    override suspend fun checkConnectivity(): Boolean {
        return try {
            transformToConnectivityException {
                val wrapperDto = graphQlClient.query(QueryType.AppInfo)
                wrapperDto.errors.forEach {
                    log.debug(it.message ?: it.path.joinToString("/"))
                }
                wrapperDto.errors.isEmpty()
            }
        } catch (e: ConnectivityException.Recoverable) {
            log.debug("no connectivity because of error: ${e.localizedMessage}")
            false
        }
    }
}