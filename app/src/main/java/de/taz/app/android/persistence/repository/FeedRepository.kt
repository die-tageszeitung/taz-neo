package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.models.Feed
import de.taz.app.android.util.SingletonHolder

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

}