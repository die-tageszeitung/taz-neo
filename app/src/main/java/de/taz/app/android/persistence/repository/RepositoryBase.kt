package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import de.taz.app.android.persistence.AppDatabase

abstract class RepositoryBase(applicationContext: Context) {

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    var appDatabase = AppDatabase.getInstance(applicationContext)


}