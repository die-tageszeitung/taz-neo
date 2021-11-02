package de.taz.app.android.util

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import de.taz.app.android.R
import io.sentry.Sentry

fun <T> reportAndRethrowExceptions(block: () -> T): T {
    try {
        return block()
    } catch (e: Exception) {
        Sentry.captureException(e)
        throw e
    }
}

fun Activity.showConnectionErrorDialog(onDismiss: () -> Unit = { finish() }) {
    AlertDialog.Builder(this)
        .setMessage(R.string.splash_error_no_connection)
        .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
        .setOnDismissListener {
            onDismiss()
        }
        .show()
}
