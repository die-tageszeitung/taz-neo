package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import de.taz.app.android.persistence.AppDatabase

abstract class RepositoryBase constructor(applicationContext: Context) {

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var appDatabase = AppDatabase.getInstance(applicationContext)


}