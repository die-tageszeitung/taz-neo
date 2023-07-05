package de.taz.app.android.util

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.taz.app.android.R
import de.taz.app.android.persistence.repository.AbstractIssuePublication
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.tracking.Tracker
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
    val dialog = MaterialAlertDialogBuilder(this)
        .setMessage(R.string.splash_error_no_connection)
        .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
        .setOnDismissListener {
            onDismiss()
        }
        .create()

    dialog.show()
    Tracker.getInstance(applicationContext).trackConnectionErrorDialog()
}


fun Activity.showFatalErrorDialog(onDismiss: () -> Unit = { finish() }) {
    val dialog = MaterialAlertDialogBuilder(this)
        .setMessage(R.string.dialog_fatal_error_description)
        .setPositiveButton(android.R.string.ok) { _, _ -> finish() }
        .setOnDismissListener {
            onDismiss()
        }
        .create()
    dialog.show()
    Tracker.getInstance(applicationContext).trackFatalErrorDialog()
}

fun Activity.showIssueDownloadFailedDialog(issuePublication: AbstractIssuePublication) {
    val dialog = MaterialAlertDialogBuilder(this)
        .setMessage(
            getString(
                R.string.error_issue_download_failed,
                DateHelper.dateToLongLocalizedString(
                    DateHelper.stringToDate(issuePublication.date)!!
                )
            )
        )
        .setPositiveButton(android.R.string.ok) { _, _ -> }
        .create()

    dialog.show()
    Tracker.getInstance(applicationContext).trackIssueDownloadErrorDialog()
}