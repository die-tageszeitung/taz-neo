package de.taz.app.android.util

import androidx.lifecycle.ViewModel

interface StableIdProvider {
    fun getId(key: String): Long
}
