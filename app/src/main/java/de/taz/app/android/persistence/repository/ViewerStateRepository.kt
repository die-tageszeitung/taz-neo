package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.models.ViewerState
import de.taz.app.android.util.SingletonHolder

class ViewerStateRepository private constructor(applicationContext: Context) :

    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<ViewerStateRepository, Context>(::ViewerStateRepository)

    fun save(displayableName: String, scrollPosition: Int) {
        save(
            ViewerState(
                displayableName,
                scrollPosition
            )
        )
    }

    fun save(viewerState: ViewerState) {
        appDatabase.viewerStateDao().insertOrReplace(viewerState)
    }

    fun saveIfNotExists(displayableName: String, scrollPosition: Int) {
        saveIfNotExists(ViewerState(
            displayableName, scrollPosition
        ))
    }

    fun saveIfNotExists(viewerState: ViewerState) {

        appDatabase.viewerStateDao().insertOrIgnore(viewerState)
    }

    fun get(displayableName: String): ViewerState? {
        return appDatabase.viewerStateDao().getByDisplayableName(displayableName)
    }
}