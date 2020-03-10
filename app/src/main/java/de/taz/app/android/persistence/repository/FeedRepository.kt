package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.lifecycle.LiveData
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.Feed
import de.taz.app.android.util.SingletonHolder

@Mockable
class FeedRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<FeedRepository, Context>(::FeedRepository)

    fun save(feeds: List<Feed>) {
        appDatabase.feedDao().insertOrReplace(feeds)
    }

    fun save(feed: Feed) {
        appDatabase.feedDao().insertOrReplace(feed)
    }

    fun get(feedName: String): Feed {
        return appDatabase.feedDao().get(feedName)
    }

    fun getAll(): List<Feed> {
        return appDatabase.feedDao().getAll()
    }

    fun getAllLiveData(): LiveData<List<Feed>> {
        return appDatabase.feedDao().getAllLiveData()
    }

}