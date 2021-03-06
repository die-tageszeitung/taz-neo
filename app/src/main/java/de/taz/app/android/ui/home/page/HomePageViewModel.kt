package de.taz.app.android.ui.home.page

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.api.models.Feed
import de.taz.app.android.persistence.repository.FeedRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.*

typealias MomentChangedListener = (Date) -> Unit

class HomePageViewModel(application: Application) : AndroidViewModel(application) {
    private val notifyMomentChangedListeners = LinkedList<MomentChangedListener>()

    private var currentFeed: String? = null

    fun setFeed(feedName: String) {
        if (currentFeed != feedName) {
            FeedRepository.getInstance(getApplication()).get(feedName)?.let {
                mutableFeedLiveData.value = it
            }
        }
    }

    fun setFeed(feed: Feed) {
        mutableFeedLiveData.value = feed
    }

    private val mutableFeedLiveData = MutableLiveData<Feed>()
    val feed: LiveData<Feed> = mutableFeedLiveData

    fun addNotifyMomentChangedListener(listener: MomentChangedListener): MomentChangedListener {
        notifyMomentChangedListeners.add(listener)
        return listener
    }

    fun removeNotifyMomentChangedListener(listener: MomentChangedListener) {
        notifyMomentChangedListeners.remove(listener)
    }

    fun notifyMomentChanged(date: Date) {
        notifyMomentChangedListeners.forEach { it.invoke(date) }
    }
}