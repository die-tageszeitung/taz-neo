package de.taz.app.android.ui.issueViewer

import android.app.Application
import android.os.Parcelable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.ArticleStubWithSectionKey
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.content.ContentService
import de.taz.app.android.content.cache.CacheOperationFailedException
import de.taz.app.android.dataStore.GeneralDataStore
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

private const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE_KEY"
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
    private val generalDataStore = GeneralDataStore.getInstance(application)
    private val issueRepository = IssueRepository.getInstance(application)
    private val sectionRepository = SectionRepository.getInstance(application)
    private val articleRepository = ArticleRepository.getInstance(application)
    private val contentService = ContentService.getInstance(application)

    val issueLoadingFailedErrorFlow = MutableStateFlow(false)
    val currentDisplayable: String?
        get() = issueKeyAndDisplayableKeyFlow.value?.displayableKey


    fun setDisplayable(issueDisplayable: IssueKeyWithDisplayableKey?) {
        log.debug("setDisplayable(${issueDisplayable?.issueKey} ${issueDisplayable?.displayableKey})")
        viewModelScope.launch {
            savedStateHandle.set(KEY_DISPLAYABLE, issueDisplayable)
            issueDisplayable?.let {
                // persist the last displayable in db
                issueRepository.saveLastDisplayable(it.issueKey, it.displayableKey)
            }
        }
    }

    suspend fun setDisplayable(
        issueKey: IssueKey,
        displayableKey: String? = null,
        loadIssue: Boolean = false,
        continueReadDirectly: Boolean = false,
    ): IssueKeyWithDisplayableKey? {
        var showContinueReadDisplayable: IssueKeyWithDisplayableKey? = null
        if (loadIssue || displayableKey == null) {
            issueLoadingFailedErrorFlow.emit(false)
            try {
                val lastDisplayable = issueRepository.getLastDisplayable(issueKey)
                val titleSectionsDisplayable = sectionRepository.getSectionStubsForIssue(issueKey).firstOrNull()?.key
                val continueReadAutomatically = generalDataStore.settingsContinueRead.get()
                val isPage = lastDisplayable?.startsWith("s") == true && lastDisplayable.endsWith(".pdf")

                if (lastDisplayable != null && lastDisplayable != titleSectionsDisplayable && !isPage) {
                    showContinueReadDisplayable = IssueKeyWithDisplayableKey(issueKey,lastDisplayable)
                }

                val isPdfMode = generalDataStore.pdfMode.get()
                // either displayable is specified, persisted or defaulted to title section
                val displayable = if (continueReadAutomatically || (continueReadDirectly && !isPdfMode)) {
                    displayableKey
                        ?: lastDisplayable
                        ?: titleSectionsDisplayable
                } else {
                    titleSectionsDisplayable
                }

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
                IssueKeyWithDisplayableKey(issueKey, displayableKey)
            )
        }
        return showContinueReadDisplayable
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

    var goNextArticle = MutableStateFlow(false)
    var goPreviousArticle = MutableStateFlow(false)
    var lastSectionKey: String?
        set(value) = savedStateHandle.set(KEY_LAST_SECTION, value)
        get() = savedStateHandle[KEY_LAST_SECTION]

    val issueKeyAndDisplayableKeyFlow: StateFlow<IssueKeyWithDisplayableKey?> =
        savedStateHandle.getStateFlow(KEY_DISPLAYABLE, null)

    val activeDisplayModeFlow: StateFlow<IssueContentDisplayMode> =
        issueKeyAndDisplayableKeyFlow.map {
            val displayableKey = it?.displayableKey
            if (displayableKey == null)
                IssueContentDisplayMode.Loading
            else if (displayableKey.startsWith("sec"))
                IssueContentDisplayMode.Section
            else
                IssueContentDisplayMode.Article
        }.stateIn(viewModelScope, SharingStarted.Eagerly, IssueContentDisplayMode.Loading)

    private val issueKeyFlow: Flow<IssueKey> = issueKeyAndDisplayableKeyFlow
        .filterNotNull()
        .map { it.issueKey }

    val displayableKeyFlow: Flow<String> =
        issueKeyAndDisplayableKeyFlow.filterNotNull().map { it.displayableKey }

    val articleListFlow: Flow<List<ArticleStubWithSectionKey>> = this.issueKeyFlow.map {
        articleRepository.getArticleStubListForIssue(it)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sectionListFlow: Flow<List<SectionStub>> = this.issueKeyFlow.map {
        sectionRepository.getSectionStubsForIssue(it)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val imprintArticleFlow: Flow<ArticleOperations?> = this.issueKeyFlow.map {
        issueRepository.getImprintStub(it)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Flow that indicates if the subscription elapsed dialog should be shown.
     * Will not emit anything until a issue is loaded.
     */
    val showSubscriptionElapsedFlow: Flow<Boolean> = combine(
        this.issueKeyFlow,
        authHelper.getShouldShowSubscriptionElapsedDialogFlow()
    ) { issueKey, shouldShowSubscriptionElapsedDialog ->
        val isPublic = issueKey.status == IssueStatus.public
        isPublic && shouldShowSubscriptionElapsedDialog
    }

    suspend fun findArticleStubByArticleName(articleName: String): ArticleStub? {
        val articleStub = articleListFlow.first().find {
            ArticleName.fromArticleFileName(it.articleStub.articleFileName) == articleName
        }?.articleStub

        if (articleStub == null) {
            val knownArticleFileNames =
                articleListFlow.first().joinToString { it.articleStub.articleFileName }
            log.warn("Could not find articleFileName for articleName=$articleName in $knownArticleFileNames")
        }
        return articleStub
    }

}
