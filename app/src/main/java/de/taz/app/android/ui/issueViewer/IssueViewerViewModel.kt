package de.taz.app.android.ui.issueViewer

import android.app.Application
import android.os.Parcelable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.asFlow
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.ArticleStubWithSectionKey
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.monkey.getApplicationScope
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.CannotDetermineBaseUrlException
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment.Companion.getShouldShowSubscriptionElapsedDialogFlow
import de.taz.app.android.util.ArticleName
import de.taz.app.android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

private const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE_KEY"
private const val KEY_DISPLAY_MODE = "KEY_DISPLAY_MODE"
private const val KEY_LAST_SECTION = "KEY_LAST_SECTION"

enum class IssueContentDisplayMode {
    Article, Section, Loading
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
    private val authHelper = AuthHelper.getInstance(application)
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
                issueRepository.saveLastDisplayable(it.issueKey, it.displayableKey)
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
                    ?: issueRepository.getLastDisplayable(issueKey)
                    ?: sectionRepository.getSectionStubsForIssue(issueKey).firstOrNull()?.key
                if (displayable != null) {
                    setDisplayable(
                        IssueKeyWithDisplayableKey(issueKey, displayable)
                    )
                    startBackgroundIssueDownload(issueKey)
                } else {
                    log.error("Could not get displayable issueKey=$issueKey and displayableKey=$displayableKey")
                    SentryWrapper.captureMessage("Error while loading displayable")
                    issueLoadingFailedErrorFlow.emit(true)
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

    private fun startBackgroundIssueDownload(issueKey: IssueKey) {
        getApplicationScope().launch {
            try {
                contentService.downloadIssuePublicationToCache(IssuePublication(issueKey))
                issueRepository.updateLastViewedDate(issueKey)
            } catch (e: CacheOperationFailedException) {
                issueLoadingFailedErrorFlow.emit(true)
            } catch (e: CannotDetermineBaseUrlException) {
                // FIXME (johannes): Workaround to #14367
                // concurrent download/deletion jobs might result in a articles missing their parent issue and thus not being able to find the base url
                issueLoadingFailedErrorFlow.emit(true)
                log.warn("Could not determine baseurl for issue with key $issueKey", e)
                SentryWrapper.captureException(e)
            }
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

    private val issueKeyFlow: Flow<IssueKey> = issueKeyAndDisplayableKeyLiveData.asFlow()
        .filterNotNull()
        .map { it.issueKey }

    private val issueKeyLiveData: LiveData<IssueKey?> =
        issueKeyAndDisplayableKeyLiveData.map { it?.issueKey }.distinctUntilChanged()

    val displayableKeyLiveData: LiveData<String?> =
        issueKeyAndDisplayableKeyLiveData.map { it?.displayableKey }.distinctUntilChanged()

    val articleListLiveData: LiveData<List<ArticleStubWithSectionKey>> =
        MediatorLiveData<List<ArticleStubWithSectionKey>>().apply {
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

    val imprintArticleLiveData: LiveData<ArticleOperations?> = MediatorLiveData<ArticleOperations?>().apply {
        addSource(issueKeyLiveData) {
            it?.let {
                viewModelScope.launch {
                    postValue(issueRepository.getImprintStub(it))
                }
            }
        }
    }

    /**
     * Flow that indicates if the subscription elapsed dialog should be shown.
     * Will not emit anything until a issue is loaded.
     */
    val showSubscriptionElapsedFlow: Flow<Boolean> = combine(
        issueKeyFlow,
        authHelper.getShouldShowSubscriptionElapsedDialogFlow()
    ) { issueKey, shouldShowSubscriptionElapsedDialog ->
        val isPublic = issueKey.status == IssueStatus.public
        isPublic && shouldShowSubscriptionElapsedDialog
    }

    fun findArticleStubByArticleName(articleName: String): ArticleStub? {
        val articleStub = articleListLiveData.value?.find {
            ArticleName.fromArticleFileName(it.articleStub.articleFileName) == articleName
        }?.articleStub

        if (articleStub == null) {
            val knownArticleFileNames = articleListLiveData.value?.joinToString { it.articleStub.articleFileName }
            log.warn("Could not find articleFileName for articleName=$articleName in $knownArticleFileNames")
        }
        return articleStub
    }

}
