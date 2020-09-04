package de.taz.app.android.singletons

import de.taz.app.android.R
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.util.Log
import java.util.*

/**
 * Singleton to create Toasts
 */
@Mockable
class ToastHelper {

    private val log by Log

    fun showToast(messageId: Int, long: Boolean = false) {
        log.info("showing toast: $messageId")
    }

    fun showToast(message: String, long: Boolean = false) {
        log.info("showing toast: $message")
    }

    private var lastConnectionError = 0L

    private var lastSomethingWentWrontToast = 0L
    fun showSomethingWentWrongToast() {
        val now = Date().time
        if (now > lastSomethingWentWrontToast+ 10000) {
            lastSomethingWentWrontToast = now
            showToast(R.string.something_went_wrong_try_later)
        }
    }


}