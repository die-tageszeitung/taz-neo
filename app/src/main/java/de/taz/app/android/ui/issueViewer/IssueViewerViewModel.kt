package de.taz.app.android.ui.issueViewer

import android.app.Application
import android.os.Parcelable
import androidx.lifecycle.*
import de.taz.app.android.TazApplication
import de.taz.app.android.api.models.*
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.util.Log
import kotlinx.parcelize.Parcelize
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

private const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE_KEY"
private const val KEY_DISPLAY_MODE = "KEY_DISPLAY_MODE"
private const val KEY_LAST_SECTION = "KEY_LAST_SECTION"

enum class IssueContentDisplayMode {
    Article, Section, Imprint, Loading
}

@Parcelize
data class IssueKeyWithDisplayableKey(
    val issueKey: IssueKey,
    val displayableKey: String
) : Parcelable

class IssueViewerViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val log by Log
    private val dataService = DataService.getInstance(application)
    private val issueRepository = IssueRepository.getInstance(application)
    private val sectionRepository = SectionRepository.getInstance(application)
    private val articleRepository = ArticleRepository.getInstance(application)
    private val contentService = ContentService.getInstance(application)

    val issueLoadingFailedErrorFlow = MutableStateFlow(false)
    val currentDisplayable: String?
        get() = issueKeyAndDisplayableKeyLiveData.value?.displayableKey


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
            viewModelScope.launch {
                dataService.saveLastDisplayableOnIssue(it.issueKey, it.displayableKey)
            }
        }
    }

    suspend fun setDisplayable(
        issueKey: IssueKey,
        displayableKey: String? = null,
        immediate: Boolean = false,
        loadIssue: Boolean = false
    ) {
        if (loadIssue || displayableKey == null) {
            issueLoadingFailedErrorFlow.emit(false)
            try {
                // either displayable is specified, persisted or defaulted to first section
                val displayable = displayableKey
                    ?: dataService.getLastDisplayableOnIssue(issueKey)
                    ?: sectionRepository.getSectionStubsForIssue(issueKey).first().key
                setDisplayable(
                    IssueKeyWithDisplayableKey(issueKey, displayable)
                )
                // Start downloading the whole issue in background
                applicationScope.launch {
                    try {
                        contentService.downloadIssuePublicationToCache(IssuePublication(issueKey))
                        issueRepository.updateLastViewedDate(issueKey)
                    } catch (e: CacheOperationFailedException) {
                        issueLoadingFailedErrorFlow.emit(true)
                    }
                }
            } catch (e: CacheOperationFailedException) {
                issueLoadingFailedErrorFlow.emit(true)
            }
        } else {
            setDisplayable(
                IssueKeyWithDisplayableKey(issueKey, displayableKey),
                immediate
            )
        }
    }

    var goNextArticle = MutableLiveData(false)
    var goPreviousArticle = MutableLiveData(false)
    var lastSectionKey: String?
        set(value) = savedStateHandle.set(KEY_LAST_SECTION, value)
        get() = savedStateHandle[KEY_LAST_SECTION]

    val issueKeyAndDisplayableKeyLiveData: MutableLiveData<IssueKeyWithDisplayableKey?> =
        savedStateHandle.getLiveData(KEY_DISPLAYABLE)
    val activeDisplayMode: MutableLiveData<IssueContentDisplayMode> =
        savedStateHandle.getLiveData(KEY_DISPLAY_MODE, IssueContentDisplayMode.Loading)

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
                        viewModelScope.launch {
                            postValue(
                                articleRepository.getArticleStubListForIssue(it)
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
                    viewModelScope.launch {
                        postValue(sectionRepository.getSectionStubsForIssue(it))
                    }
                } ?: run {
                    postValue(emptyList())
                }
            }
        }

    val imprintArticleLiveData: LiveData<Article?> = MediatorLiveData<Article?>().apply {
        addSource(issueKeyLiveData) {
            it?.let {
                viewModelScope.launch {
                    postValue(issueRepository.getImprint(it))
                }
            }
        }
    }

    private val applicationScope by lazy {
        (application as TazApplication).applicationScope
    }
}
