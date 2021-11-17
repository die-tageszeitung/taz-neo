package de.taz.app.android.download


/**
 * Priorities for scheduling downloads
 * Ordinal of enum is the natural comparable - so first item in list has lowest prio, last item highest
 */
enum class DownloadPriority {
    Normal,
    High
}
