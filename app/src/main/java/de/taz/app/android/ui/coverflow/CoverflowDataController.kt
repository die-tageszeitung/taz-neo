package de.taz.app.android.ui.coverflow

import androidx.lifecycle.*
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.util.PREFERENCES_FEEDS_INACTIVE
import de.taz.app.android.util.PreferencesHelper
import de.taz.app.android.util.SharedPreferenceStringSetLiveData

open class CoverflowDataController : BaseDataController(), CoverflowContract.DataController {

    override val feeds: LiveData<Map<String, Feed>> = Transformations.map(
        FeedRepository.getInstance().getAllLiveData()
    ) { it.associateBy { feed -> feed.name } }


    private val inactiveFeedNameLiveData = SharedPreferenceStringSetLiveData(
        PreferencesHelper.getInstance().feedPreferences, PREFERENCES_FEEDS_INACTIVE, emptySet()
    )

    private val issueStubsLiveData: LiveData<List<IssueStub>> =
        IssueRepository.getInstance().getAllStubsLiveData()

    private fun filterIssuesByActiveFeeds(issues: List<IssueStub>, inactiveFeedNames: Set<String>): List<IssueStub> {
        return issues.filter { it.feedName !in inactiveFeedNames }.reversed()
    }

    /**
     * LiveData listening on each the active feeds and the issuerepository updating "active issues" on each update
     */
    override val visibleIssueStubsLiveData = MediatorLiveData<List<IssueStub>?>().apply {
        addSource(inactiveFeedNameLiveData) { inactiveFeeds ->
            this.value = issueStubsLiveData.value?.let {
                filterIssuesByActiveFeeds(it, inactiveFeeds)
            }
        }
        addSource(issueStubsLiveData) { issueStubs ->
                this.value = inactiveFeedNameLiveData.value?.let {
                    filterIssuesByActiveFeeds(issueStubs, it)
                }
            }
        }

}