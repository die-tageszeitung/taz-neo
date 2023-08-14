package de.taz.app.android.persistence.repository

import android.content.Context
import de.taz.app.android.api.models.Feed
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first


class FeedRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<FeedRepository, Context>(::FeedRepository)

    suspend fun save(feeds: List<Feed>) {
        appDatabase.feedDao().insertOrReplace(feeds)
    }

    suspend fun save(feed: Feed) {
        appDatabase.feedDao().insertOrReplace(feed)
    }

    suspend fun get(feedName: String): Feed? {
        return getFlow(feedName).first()
    }

    fun getFlow(feedName: String): Flow<Feed?> {
        return appDatabase.feedDao().get(feedName)
    }
}