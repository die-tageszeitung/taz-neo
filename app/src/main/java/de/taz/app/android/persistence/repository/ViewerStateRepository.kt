package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.models.ViewerState
import de.taz.app.android.util.SingletonHolder

class ViewerStateRepository private constructor(applicationContext: Context) :

    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<ViewerStateRepository, Context>(::ViewerStateRepository)

    suspend fun save(displayableName: String, scrollPosition: Int) {
        save(
            ViewerState(
                displayableName,
                scrollPosition
            )
        )
    }

    suspend fun save(viewerState: ViewerState) {
        appDatabase.viewerStateDao().insertOrReplace(viewerState)
    }

    suspend fun saveIfNotExists(displayableName: String, scrollPosition: Int) {
        saveIfNotExists(ViewerState(
            displayableName, scrollPosition
        ))
    }

    suspend fun saveIfNotExists(viewerState: ViewerState) {

        appDatabase.viewerStateDao().insertOrIgnore(viewerState)
    }

    suspend fun get(displayableName: String): ViewerState? {
        return appDatabase.viewerStateDao().getByDisplayableName(displayableName)
    }
}