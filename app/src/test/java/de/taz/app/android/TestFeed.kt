package de.taz.app.android

import de.taz.app.android.api.dto.Cycle
import de.taz.app.android.api.models.Feed

val testFeed = Feed("test", Cycle.daily, 0.5f, "1337-12-13")
val testFeeds: List<Feed> = listOf(testFeed)
