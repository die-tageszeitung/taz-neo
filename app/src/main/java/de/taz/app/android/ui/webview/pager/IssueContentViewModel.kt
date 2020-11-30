package de.taz.app.android.ui.webview.pager

import android.app.Application
import android.os.Parcelable
import androidx.lifecycle.*
import de.taz.app.android.api.models.*
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.util.Log
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE_KEY"
private const val KEY_SCROLL_POSITION = "KEY_SCROLL_POSITION"
private const val KEY_LAST_SECTION = "KEY_LAST_SECTION"

enum class IssueContentDisplayMode {
    Article, Section, Imprint, Loading
}

@Parcelize
data class IssueKeyWithDisplayableKey(
    val issueKey: IssueKey,
    val displayableKey: String
) : Parcelable

data class IssueStubWithDisplayableKey(
    val issueStub: IssueStub,
    val displayableKey: String
)

@Parcelize
data class DisplayableScrollposition(
    val displayableKey: String,
    val scrollPosition: Int
) : Parcelable

class IssueContentViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val log by Log
    private val dataService = DataService.getInstance()

    val currentDisplayable: String?
        get() = issueKeyAndDisplayableKeyLiveData.value?.displayableKey

    var lastScrollPositionOnDisplayable: DisplayableScrollposition?
        get() = savedStateHandle.get(KEY_SCROLL_POSITION)
        set(value) = savedStateHandle.set(KEY_SCROLL_POSITION, value)

    fun setDisplayable(issueDisplayable: IssueKeyWithDisplayableKey?, immediate: Boolean = false) {
        log.debug("setDisplayable(${issueDisplayable?.issueKey} ${issueDisplayable?.displayableKey}")
        if (issueDisplayable == null) {
            activeDisplayMode.value = IssueContentDisplayMode.Loading
        }
        if (immediate) {
            issueKeyAndDisplayableKeyLiveData.value = issueDisplayable
        } else {
            issueKeyAndDisplayableKeyLiveData.postValue(
                issueDisplayable
            )
        }
        issueDisplayable?.let {
            // persist the last displayable in db
            CoroutineScope(Dispatchers.IO).launch {
                dataService.saveLastDisplayableOnIssue(it.issueKey, it.displayableKey)
            }
        }
    }

    fun setDisplayable(issueKey: IssueKey, displayableKey: String, immediate: Boolean = false) {
        setDisplayable(
            IssueKeyWithDisplayableKey(issueKey, displayableKey),
            immediate
        )
    }

    /**
     * If not specifying the displayableKey we assume to just display the very first section
     */
    fun setDisplayable(issue: Issue, immediate: Boolean = false) {
        log.debug("Showing issue defaulting to first section")
        setDisplayable(
            IssueKeyWithDisplayableKey(issue.issueKey, issue.sectionList.first().key),
            immediate
        )
    }

    suspend fun setDisplayable(issueStub: IssueStub, immediate: Boolean = false) {
        log.debug("Showing issue defaulting to first section")
        // Either fetch last displayable from DB or take first section
        val displayableKey = dataService.getLastDisplayableOnIssue(issueStub.issueKey)
            ?: SectionRepository.getInstance().getSectionStubsForIssue(issueStub.issueKey).first().key
        setDisplayable(
            IssueKeyWithDisplayableKey(issueStub.issueKey, displayableKey),
            immediate
        )
    }

    var lastSectionKey: String?
        set(value) = savedStateHandle.set(KEY_LAST_SECTION, value)
        get() = savedStateHandle.get(KEY_LAST_SECTION)

    val issueKeyAndDisplayableKeyLiveData: MutableLiveData<IssueKeyWithDisplayableKey?> =
        savedStateHandle.getLiveData(KEY_DISPLAYABLE)
    val activeDisplayMode: MutableLiveData<IssueContentDisplayMode> =
        MutableLiveData(IssueContentDisplayMode.Section)

    private val issueKeyLiveData: LiveData<IssueKey?> =
        issueKeyAndDisplayableKeyLiveData.map { it?.issueKey }

    val displayableKeyLiveData: LiveData<String?> =
        issueKeyAndDisplayableKeyLiveData.map { it?.displayableKey }

    val articleListLiveData: LiveData<List<ArticleStub>> =
        MediatorLiveData<List<ArticleStub>>().apply {
            var lastIssueKey: IssueKey? = null
            addSource(issueKeyLiveData) {
                it?.let {
                    if (it != lastIssueKey) {
                        lastIssueKey = it
                        viewModelScope.launch(Dispatchers.IO) {
                            postValue(
                                ArticleRepository.getInstance().getArticleStubListForIssue(it)
                            )
                        }
                    }
                } ?: run {
                    postValue(emptyList())
                }
            }
        }

    val sectionListLiveData: LiveData<List<SectionStub>> =
        MediatorLiveData<List<SectionStub>>().apply {
            addSource(issueKeyLiveData) {
                it?.let {
                    viewModelScope.launch(Dispatchers.IO) {
                        postValue(SectionRepository.getInstance().getSectionStubsForIssue(it))
                    }
                } ?: run {
                    postValue(emptyList())
                }
            }
        }

    val imprintArticleLiveData: LiveData<Article?> = MediatorLiveData<Article?>().apply {
        addSource(issueKeyLiveData) {
            it?.let {
                viewModelScope.launch(Dispatchers.IO) {
                    postValue(IssueRepository.getInstance().getImprint(it))
                }
            }
        }
    }
}
