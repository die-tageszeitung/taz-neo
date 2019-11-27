package de.taz.app.android.util

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import de.taz.app.android.api.models.Feed

const val PREFERENCES_FEEDS_FILE = "preferences_feeds"
const val PREFERENCES_FEEDS_INACTIVE = "inactiveFeeds"

open class PreferencesHelper private constructor(applicationContext: Context) {

    companion object : SingletonHolder<PreferencesHelper, Context>(::PreferencesHelper)

    val feedPreferences: SharedPreferences =
        applicationContext.getSharedPreferences(PREFERENCES_FEEDS_FILE, Context.MODE_PRIVATE)

    open fun activateFeed(feed: Feed) {
        val oldInactiveFeeds = feedPreferences.getStringSet(PREFERENCES_FEEDS_INACTIVE, emptySet())
        val inactiveFeeds = mutableSetOf<String>()
        oldInactiveFeeds?.forEach {
            if (feed.name != it) {
                inactiveFeeds.add(it)
            }
        }
        feedPreferences.edit().putStringSet(PREFERENCES_FEEDS_INACTIVE, inactiveFeeds).apply()
    }


    open fun deactivateFeed(feed: Feed) {
        var inactiveFeeds = feedPreferences.getStringSet(PREFERENCES_FEEDS_INACTIVE, emptySet())

        if (inactiveFeeds?.contains(feed.name) != true) {
            val tmpInactiveFeeds = mutableSetOf<String>()
            tmpInactiveFeeds.addAll(inactiveFeeds ?: emptySet())
            tmpInactiveFeeds.add(feed.name)
            inactiveFeeds = tmpInactiveFeeds
        }

        feedPreferences.edit().putStringSet(PREFERENCES_FEEDS_INACTIVE, inactiveFeeds).apply()
    }

    open fun computeFontSize(percentage: String) : String {
        val fontSize = percentage.toInt() * 0.01 * 18
        return fontSize.toString()
    }
}
