package de.taz.app.android.persistence.entities

import androidx.room.Ignore
import de.taz.app.android.persistence.AppDatabase

abstract class GenericEntity<T> {

    @Ignore protected val appDatabase = AppDatabase.getInstance()

    abstract fun toObject(): T
}