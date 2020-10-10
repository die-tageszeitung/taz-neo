package de.taz.app.android.ui.webview.pager

import android.app.Application
import android.os.Parcelable
import androidx.lifecycle.*
import de.taz.app.android.api.models.*
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.util.Log
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE_KEY"
private const val KEY_SCROLL_POSITION = "KEY_SCROLL_POSITION"
private const val KEY_LAST_SECTION = "KEY_LAST_SECTION"

enum class IssueContentDisplayMode {
    Article, Section, Imprint
}

@Parcelize
data class IssueKeyWithDisplayableKey(
    val issueKey: IssueKey,
    val displayableKey: String
): Parcelable

@Parcelize
data class DisplayableScrollposition(
    val displayableKey: String,
    val scrollPosition: Int
): Parcelable

class IssueContentViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val log by Log

    var lastScrollPositionOnDisplayable: DisplayableScrollposition?
        get() = savedStateHandle.get(KEY_SCROLL_POSITION)
        set(value) = savedStateHandle.set(KEY_SCROLL_POSITION, value)

    fun setDisplayable(issueDisplayable: IssueKeyWithDisplayableKey) {
        issueKeyAndDisplayableKeyLiveData.postValue(
            issueDisplayable
        )
    }
    fun setDisplayable(issueKey: IssueKey, displayableKey: String) {
        log.debug("Setting displayable issueKey with displayableKey")
        setDisplayable(
            IssueKeyWithDisplayableKey(issueKey, displayableKey)
        )
    }

    /**
     * If not specifying the displayableKey we assume to just display the very virst section
     */
    fun setDisplayable(issue: Issue) {
        log.debug("Showing issue defaulting to first section")
        setDisplayable(
            IssueKeyWithDisplayableKey(issue.issueKey, issue.sectionList.first().key)
        )
    }

    /**
     * If not specifying the issueKey we will derive it from the displayable
     */
    suspend fun setDisplayable(displayableKey: String) = withContext(Dispatchers.IO) {
        val derrivedIssue = IssueRepository.getInstance().let {
            it.getIssueStubForArticle(displayableKey) ?:
            it.getIssueStubForSection(displayableKey) ?:
            it.getIssueStubByImprintFileName(displayableKey)
        }
        derrivedIssue?.let {
            setDisplayable(
                IssueKeyWithDisplayableKey(it.issueKey, displayableKey)
            )
        } ?: throw IllegalStateException(
            "Setting a displayable on the IssueContentViewModel is illegal if no issue is set yet"
        )
    }

    private var currentIssueStub: IssueStub? = null

    var lastSectionKey: String?
        set(value) = savedStateHandle.set(KEY_LAST_SECTION, value)
        get() = savedStateHandle.get(KEY_LAST_SECTION)

    private val issueKeyAndDisplayableKeyLiveData: MutableLiveData<IssueKeyWithDisplayableKey> =
        savedStateHandle.getLiveData(KEY_DISPLAYABLE)
    val activeDisplayMode: MutableLiveData<IssueContentDisplayMode> = MutableLiveData(IssueContentDisplayMode.Section)

    private val issueKeyLiveData: LiveData<IssueKey> =
        issueKeyAndDisplayableKeyLiveData.map { it.issueKey }

    val issueStubAndDisplayableKeyLiveData: LiveData<Pair<IssueStub, String>> =
        MediatorLiveData<Pair<IssueStub, String>>().apply {
            addSource(issueKeyAndDisplayableKeyLiveData) { (issueKey, displayableKey) ->
                viewModelScope.launch(Dispatchers.IO) {
                    if (currentIssueStub?.issueKey != issueKey) {
                        currentIssueStub = IssueRepository.getInstance()
                            .getIssueStubByIssueKey(issueKey)
                        postValue(
                            currentIssueStub!! to displayableKey
                        )
                    } else {
                        postValue(currentIssueStub!! to displayableKey)
                    }
                }
            }
        }

    val currentIssue: IssueStub?
        get() = issueStubAndDisplayableKeyLiveData.value?.first

    val displayableKeyLiveData: LiveData<String> =
        issueKeyAndDisplayableKeyLiveData.map { it.displayableKey }

    val articleListLiveData: LiveData<List<ArticleStub>> =
        MediatorLiveData<List<ArticleStub>>().apply {
            addSource(issueKeyLiveData) {
                viewModelScope.launch(Dispatchers.IO) {
                    postValue(ArticleRepository.getInstance().getArticleStubListForIssue(it))
                }
            }
        }

    val sectionListLiveData: LiveData<List<SectionStub>> =
        MediatorLiveData<List<SectionStub>>().apply {
            addSource(issueKeyLiveData) {
                viewModelScope.launch(Dispatchers.IO) {
                    postValue(SectionRepository.getInstance().getSectionStubsForIssue(it))
                }
            }
        }

    val imprintArticleLiveData: LiveData<ArticleStub?> = MediatorLiveData<ArticleStub?>().apply {
        addSource(issueKeyLiveData) {
            viewModelScope.launch(Dispatchers.IO) {
                postValue(IssueRepository.getInstance().getImprint(it))
            }
        }
    }
}
