package de.taz.app.android.util

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import de.taz.app.android.R
import de.taz.app.android.persistence.repository.AbstractIssueKey
import de.taz.app.android.singletons.DateHelper
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


fun Activity.showFatalErrorDialog(onDismiss: () -> Unit = { finish() }) {
    AlertDialog.Builder(this)
        .setMessage(R.string.dialog_fatal_error_description)
        .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
        .setOnDismissListener {
            onDismiss()
        }
        .show()
}

fun Activity.showIssueDownloadFailedDialog(issueKey: AbstractIssueKey) {
    android.app.AlertDialog.Builder(this)
        .setMessage(
            getString(
                R.string.error_issue_download_failed,
                DateHelper.dateToLongLocalizedString(
                    DateHelper.stringToDate(issueKey.date)!!
                )
            )
        )
        .setPositiveButton(android.R.string.ok) { _, _ -> }
        .show()
}