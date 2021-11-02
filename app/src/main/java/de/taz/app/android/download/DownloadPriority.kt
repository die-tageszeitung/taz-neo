package de.taz.app.android.download


/**
 * Priorities for scheduling downloads
 * Ordinal of enum is the natural comparable - so first item in list has highest prio, last item least
 */
enum class DownloadPriority {
    Normal,
    High
}
