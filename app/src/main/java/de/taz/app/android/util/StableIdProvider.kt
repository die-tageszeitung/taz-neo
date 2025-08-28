package de.taz.app.android.util

interface StableIdProvider {
    fun getId(key: String): Long
}
