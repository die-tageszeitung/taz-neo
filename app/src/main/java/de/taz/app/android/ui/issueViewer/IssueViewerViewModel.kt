package de.taz.app.android.ui.issueViewer

import android.app.Application
import android.os.Parcelable
import androidx.lifecycle.*
import de.taz.app.android.api.models.*
import de.taz.app.android.data.DataService
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.util.Log
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE_KEY"
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
    private val toastHelper = ToastHelper.getInstance(application)

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
            CoroutineScope(Dispatchers.IO).launch {
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
        var noConnectionShown = false
        fun onConnectionFailure() {
            if (!noConnectionShown) {
                viewModelScope.launch {
                    toastHelper.showNoConnectionToast()
                    noConnectionShown = true
                }
            }
        }
        if (loadIssue || displayableKey == null) {
            activeDisplayMode.postValue(IssueContentDisplayMode.Loading)
            withContext(Dispatchers.IO) {
                val issue = dataService.getIssue(
                    IssuePublication(issueKey),
                    retryOnFailure = true,
                    onConnectionFailure = {
                        onConnectionFailure()
                    })

                dataService.ensureDownloaded(
                    issue,
                    skipIntegrityCheck = false,
                    onConnectionFailure = ::onConnectionFailure
                )

                // either displayable is specified, persisted or defaulted to first section
                val displayable = displayableKey
                    ?: dataService.getLastDisplayableOnIssue(issueKey)
                    ?: sectionRepository.getSectionStubsForIssue(issue.issueKey).first().key
                setDisplayable(
                    IssueKeyWithDisplayableKey(issue.issueKey, displayable)
                )
            }
        } else {
            setDisplayable(
                IssueKeyWithDisplayableKey(issueKey, displayableKey),
                immediate
            )
        }
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
                    viewModelScope.launch(Dispatchers.IO) {
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
                viewModelScope.launch(Dispatchers.IO) {
                    postValue(issueRepository.getImprint(it))
                }
            }
        }
    }
}
