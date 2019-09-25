package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.annotation.VisibleForTesting
import de.taz.app.android.persistence.AppDatabase
import de.taz.app.android.util.Log

abstract class RepositoryBase constructor(applicationContext: Context) {

    private val log by Log

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    var appDatabase = AppDatabase.getInstance(applicationContext)


}