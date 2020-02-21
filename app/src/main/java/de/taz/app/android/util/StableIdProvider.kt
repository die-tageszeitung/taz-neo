package de.taz.app.android.util

import androidx.lifecycle.ViewModel

interface StableIdProvider {
    fun getId(key: String): Long
}

class StableIdViewModel: ViewModel(),
    StableIdProvider {
    private val keyList = mutableListOf<String>()

    override fun getId(key: String): Long {
        var position = keyList.indexOf(key)
        if (position < 0) {
            position = keyList.size
            keyList.add(key)
        }
        return position.toLong()
    }
}
