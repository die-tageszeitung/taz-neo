package de.taz.app.android.data

import java.lang.Exception


sealed class DataException(message: String, override val cause: Throwable?): Exception(message, cause) {
    class NoValidSubscriptionException(message: String = "No valid subscription", cause: Throwable? = null): DataException(message, cause)
    class InconsistentRequestException(message: String = "The request cannot be fulfilled because it's parameters are inconsistent", cause: Throwable? = null): DataException(message, cause)
}
