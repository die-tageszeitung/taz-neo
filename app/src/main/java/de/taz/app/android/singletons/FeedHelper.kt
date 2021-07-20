package de.taz.app.android.singletons

import android.content.Context
import android.content.SharedPreferences
import de.taz.app.android.annotation.Mockable
import de.taz.app.android.api.models.Feed
import de.taz.app.android.util.SingletonHolder

const val PREFERENCES_FEEDS_FILE = "preferences_feeds"
const val PREFERENCES_FEEDS_INACTIVE = "inactiveFeeds"

@Mockable
class FeedHelper private constructor(applicationContext: Context) {

    companion object : SingletonHolder<FeedHelper, Context>(::FeedHelper)

    val feedPreferences: SharedPreferences =
        applicationContext.getSharedPreferences(PREFERENCES_FEEDS_FILE, Context.MODE_PRIVATE)

    fun deactivateFeed(feed: Feed) {
        var inactiveFeeds = feedPreferences.getStringSet(PREFERENCES_FEEDS_INACTIVE, emptySet())

        if (inactiveFeeds?.contains(feed.name) != true) {
            val tmpInactiveFeeds = mutableSetOf<String>()
            tmpInactiveFeeds.addAll(inactiveFeeds ?: emptySet())
            tmpInactiveFeeds.add(feed.name)
            inactiveFeeds = tmpInactiveFeeds
        }

        feedPreferences.edit().putStringSet(PREFERENCES_FEEDS_INACTIVE, inactiveFeeds).apply()
    }
}
