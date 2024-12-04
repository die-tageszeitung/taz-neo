package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.withTransaction
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.ConnectivityException
import de.taz.app.android.api.dto.BookmarkRepresentation
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleBookmarkTime
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.SynchronizeFromType
import de.taz.app.android.content.ContentService
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.tracking.Tracker
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Date

/**
 * The BookmarkRepository stores the Bookmarking State for each Article, and allows to access its
 * state via flows.
 * It is not working directly on the database, but holds a local state.
 */
class BookmarkRepository(
    applicationContext: Context,
    private val articleStubToArticle: suspend (ArticleStub) -> Article?,
    private val coroutineScope: CoroutineScope,
    private val tracker: Tracker
) : RepositoryBase(applicationContext) {
    private constructor(applicationContext: Context) : this(
        applicationContext,
        ArticleRepository.getInstance(applicationContext)::articleStubToArticle,
        CoroutineScope(Dispatchers.Default),
        Tracker.getInstance(applicationContext)
    )

    companion object : SingletonHolder<BookmarkRepository, Context>(::BookmarkRepository)

    private val apiService = ApiService.getInstance(applicationContext)
    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val bookmarkSynchronizationRepository =
        BookmarkSynchronizationRepository.getInstance(applicationContext)
    private val contentService = ContentService.getInstance(applicationContext)
    private val generalDataStore = GeneralDataStore.getInstance(applicationContext)
    private val issueRepository = IssueRepository.getInstance(applicationContext)
    private val momentRepository = MomentRepository.getInstance(applicationContext)

    // When iterating a set we have to prevent structural changes.
    // This is why we lock changes to the set
    private val changeMutex = Mutex()
    private var state: MutableSet<String> = HashSet()

    // This Flow is only used to inform any collector on changes.
    // Due to performance reasons we are using a mutable map for our states
    // instead of passing new instances of it to this Flow.
    // We keep a replay of 1 to force new derived bookmark state flows to emit the initial state immediately.
    private var stateChangeFlow: MutableSharedFlow<Unit> = MutableSharedFlow(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        coroutineScope.launch {
            loadBookmarksFromDb()
        }
    }

    // Has to be called after any changes to the bookmarkState to allow to update the
    // individual bookmark state flows.
    // Has to be called after the DB was updated for [getBookmarkedArticlesFlow] to return a valid result
    private suspend fun triggerStateFlowUpdate() {
        stateChangeFlow.emit(Unit)
    }

    fun toggleBookmarkAsync(article: ArticleOperations): Deferred<Boolean> =
        toggleBookmarkAsync(article.key, article.mediaSyncId, article.issueDate)

    private fun toggleBookmarkAsync(
        articleFileName: String,
        mediaSyncId: Int?,
        articleDate: String
    ): Deferred<Boolean> {
        return coroutineScope.async { toggleBookmark(articleFileName, mediaSyncId, articleDate) }
    }

    /**
     * Return [Boolean] new bookmark status of the article
     */
    private suspend fun toggleBookmark(
        articleFileName: String,
        mediaSyncId: Int?,
        articleDate: String
    ): Boolean {
        val wasBookmarked: Boolean
        changeMutex.withLock {
            wasBookmarked = state.remove(articleFileName)
            if (!wasBookmarked) {
                state.add(articleFileName)
            }
        }

        // Sync the new state with the database
        if (wasBookmarked) {
            setArticleBookmark(articleFileName, null)
            handleRemoveBookmarkWhenSynchronizationIsEnabled(mediaSyncId)
            tracker.trackRemoveBookmarkEvent(articleFileName, mediaSyncId)
        } else {
            setArticleBookmark(articleFileName, Date())
            handleSaveBookmarkWhenSynchronizationIsEnabled(mediaSyncId, articleDate)
            tracker.trackAddBookmarkEvent(articleFileName, mediaSyncId)
        }
        triggerStateFlowUpdate()
        return !wasBookmarked
    }

    /**
     * To not end in a sync loop we need the [fromRemote] flag. This indicates that the bookmark is
     * added because of synchronisation and will not trigger synchronization though.
     */
    fun addBookmarkAsync(article: ArticleOperations, fromRemote: Boolean = false): Deferred<Unit> =
        addBookmarkAsync(article.key, article.mediaSyncId, article.issueDate, fromRemote)

    fun addBookmarkAsync(articleStub: ArticleStub): Deferred<Unit> =
        addBookmarkAsync(
            articleStub.articleFileName,
            articleStub.mediaSyncId,
            articleStub.issueDate,
            fromRemote = false
        )

    private fun addBookmarkAsync(
        articleFileName: String,
        mediaSyncId: Int?,
        articleDate: String,
        fromRemote: Boolean
    ): Deferred<Unit> {
        return coroutineScope.async {
            addBookmark(
                articleFileName,
                mediaSyncId,
                articleDate,
                fromRemote
            )
        }
    }

    suspend fun addBookmark(article: ArticleOperations) =
        addBookmark(article.key, article.mediaSyncId, article.issueDate, fromRemote = false)

    private suspend fun addBookmark(
        articleFileName: String,
        mediaSyncId: Int?,
        articleDate: String,
        fromRemote: Boolean
    ) {
        changeMutex.withLock {
            state.add(articleFileName)
        }
        setArticleBookmark(articleFileName, Date())
        if (!fromRemote) {
            handleSaveBookmarkWhenSynchronizationIsEnabled(mediaSyncId, articleDate)
        }
        tracker.trackAddBookmarkEvent(articleFileName, mediaSyncId)
        triggerStateFlowUpdate()
    }

    /**
     * To not end in a sync loop we need the [fromRemote] flag. This indicates that the bookmark is
     * removed because of synchronisation and will not trigger synchronization again.
     */
    fun removeBookmarkAsync(article: ArticleOperations, fromRemote: Boolean = false): Deferred<Unit> =
        removeBookmarkAsync(article.key, article.mediaSyncId, fromRemote)

    private fun removeBookmarkAsync(
        articleFileName: String,
        mediaSyncId: Int?,
        fromRemote: Boolean = false
    ): Deferred<Unit> {
        return coroutineScope.async { removeBookmark(articleFileName, mediaSyncId, fromRemote) }
    }

    private suspend fun removeBookmark(
        articleFileName: String,
        mediaSyncId: Int?,
        fromRemote: Boolean
    ) {
        changeMutex.withLock {
            state.remove(articleFileName)
        }
        setArticleBookmark(articleFileName, null)
        if (!fromRemote) {
            handleRemoveBookmarkWhenSynchronizationIsEnabled(mediaSyncId)
        }
        tracker.trackRemoveBookmarkEvent(articleFileName, mediaSyncId)
        triggerStateFlowUpdate()
    }

    /**
     * Must be called whenever an [ArticleStub] is updated with new Bookmark data from outside [BookmarkRepository].
     */
    suspend fun invalidate() {
        loadBookmarksFromDb()
    }

    // fully resets the bookmark state
    private suspend fun loadBookmarksFromDb() {
        changeMutex.withLock {
            val bookmarkedArticleStubs = getBookmarkedArticleStubs()
            val bookmarkedArticleFileNames = bookmarkedArticleStubs.map { it.articleFileName }

            state.apply {
                clear()
                // even if we have an articleName two times from a public and regular entry it will only be reflected once in the set
                addAll(bookmarkedArticleFileNames)
            }
            triggerStateFlowUpdate()
        }
    }

    /**
     * Create a new Flow that returns the bookmark state for a single article file name.
     * It will only emit changes to the state. Thus each call to this function creates a closure
     * that holds the previous bookmark state.
     */
    fun createBookmarkStateFlow(articleFileName: String): Flow<Boolean> {
        var wasBookmarked: Boolean? = null
        return stateChangeFlow.transform {
            // No need to lock on the changeMutex here. Even if there is a concurrent modification
            // on the bookmarkState we will be informed again after it went through
            val isBookmarked = state.contains(articleFileName)
            if (isBookmarked != wasBookmarked) {
                // Only emit changes to the bookmark state.
                // First state will always be emitted, as wasBookmarked is initially null
                emit(isBookmarked)
            }
            wasBookmarked = isBookmarked
        }
    }

    /**
     * Filter the list of article file names by those who are bookmarked.
     */
    fun filterIsBookmarked(articleFileNames: List<String>): List<String> {
        return articleFileNames.filter {
            state.contains(it)
        }
    }

    /**
     * Get a flow of all bookmarked [Article]s.
     * The flow updates when the bookmarks change, but **not** if the [Article]s themselves are modified
     */
    fun getBookmarkedArticlesFlow(): Flow<List<Article>> {
        return stateChangeFlow.map {
            getBookmarkedArticleStubs()
                .mapNotNull { articleStubToArticle(it) }
        }
    }

    /**
     * Get all bookmarked [ArticleStub]s
     */
    suspend fun getBookmarkedArticleStubs(): List<ArticleStub> {
        return appDatabase.articleDao().getBookmarkedArticles()
    }

    /**
     * Migrate public bookmarks on login:
     * Bookmarks for public article will be removed and instead added to the regular article counterpart.
     */
    suspend fun migratePublicBookmarks() {
        appDatabase.withTransaction {
            val bookmarkedArticleBookmarks = appDatabase.articleDao()
                .getBookmarkedArticleBookmarks()

            for (bookmarkedArticleBookmark in bookmarkedArticleBookmarks) {
                val publicArticleFileName = bookmarkedArticleBookmark.articleFileName
                val regularArticleFileName = publicArticleFileName.replace("public.", "")

                // Update the bookmarks if the bookmark is still pointing to the the public article
                // and a regular article is stored in the database.
                if (regularArticleFileName != publicArticleFileName
                    && appDatabase.articleDao().get(regularArticleFileName) != null
                ) {
                    appDatabase.articleDao().apply {
                        updateBookmarkedTime(
                            ArticleBookmarkTime(
                                regularArticleFileName,
                                bookmarkedArticleBookmark.bookmarkedTime
                            )
                        )
                        updateBookmarkedTime(ArticleBookmarkTime(publicArticleFileName, null))
                    }
                }
            }
        }
        loadBookmarksFromDb()
    }

    private suspend fun setArticleBookmark(articleFileName: String, date: Date?) {
        // FIXME (johannes): Consider getting the bookmark state after it was tried to be set.
        //   If the ArticleStub defined by articleFileName is not in the DB yet, the
        //   updateBookmarkedTime won't have any effect. But we did already change the local state.
        //   This might result in faulty behavior when bookmarking articles from section webviews.
        //   Note that it could also be a Concurrency problem that bookmarked states do not preserve,
        //   if for some reason a concurrent update is happening that is writing the whole DAO
        //   including an outdated bookmarkTime.
        appDatabase.articleDao().updateBookmarkedTime(ArticleBookmarkTime(articleFileName, date))
    }

    // region bookmark synchronization functions
    // TODO(eike): Move all those functions of this region to a BookmarkSyncService Singleton!

    /**
     * Get all remote bookmark and compare them with the local ones. Handle then accordingly.
     */
    suspend fun checkForSynchronizedBookmarks() {
        log.verbose("Check for bookmarks to synchronize")
        try {
            val remoteBookmarks = apiService.getSynchronizedBookmarks()
            if (remoteBookmarks != null) {
                val currentBookmarks = getBookmarkedArticleStubs().mapNotNull {
                    it.mediaSyncId?.let { mediaSyncId ->
                        BookmarkRepresentation(
                            mediaSyncId, it.issueDate
                        )
                    }
                }
                val newRemoteBookmarks = remoteBookmarks.filterNot { remote ->
                    remote.mediaSyncId in currentBookmarks.map { it.mediaSyncId }
                }
                log.verbose("New remote bookmarks to synchronize: ${newRemoteBookmarks.map { it.mediaSyncId }}")

                val localButNotOnRemote = currentBookmarks.filterNot { current ->
                    current.mediaSyncId in remoteBookmarks.map { it.mediaSyncId }
                }
                log.verbose("local bookmarks not on remote: ${localButNotOnRemote.map { it.mediaSyncId }}")

                handleLocalButNotOnRemoteBookmarks(localButNotOnRemote)
                handleRemoteButNotLocalBookmarks(newRemoteBookmarks)
            }
        } catch (ce: ConnectivityException) {
            log.warn("${ce.message}")
        } catch (e: Exception) {
            log.warn("$e")
            val msg = "Something went wrong fetching synchronized bookmarks"
            log.warn(msg)
            SentryWrapper.captureException(e)
        }
    }

    suspend fun checkForSynchronizedBookmarksIfEnabled() {
        val bookmarksSynchronizationEnabled =
            generalDataStore.bookmarksSynchronizationEnabled.get()
        if (bookmarksSynchronizationEnabled) {
            checkForSynchronizedBookmarks()
        }
    }

    /**
     * Find out if [remoteButNotLocalBookmarks] need to be synchronized or added locally.
     * Therefore we look at the timestamp of when we locally changed it:
     */
    suspend fun handleRemoteButNotLocalBookmarks(remoteButNotLocalBookmarks: List<BookmarkRepresentation>) {
        // check if need to be deleted on remote or added locally:
        remoteButNotLocalBookmarks.forEach {
            val bookmarkSynchronization = bookmarkSynchronizationRepository.get(it.mediaSyncId)
            if (bookmarkSynchronization?.locallyChangedTime != null) {
                deleteRemoteBookmark(it.mediaSyncId)
            } else {
                addLocalBookmark(it)
            }
        }
    }

    private suspend fun addLocalBookmark(newRemoteBookmarks: BookmarkRepresentation) {
        var articleStub = articleRepository.getStubByMediaSyncId(newRemoteBookmarks.mediaSyncId)
        // If not already in db download metadata
        var issue: IssueOperations? = null
        if (articleStub == null) {
            issue = contentService.downloadIssueMetadata(newRemoteBookmarks.date)
        }

        articleStub =
            articleStub ?: articleRepository.getStubByMediaSyncId(newRemoteBookmarks.mediaSyncId)
        if (articleStub != null) {
            // Download article
            val article = contentService.downloadArticle(
                articleStub.articleFileName, articleStub.issueDate
            )
            // Download Issue meta data to display issues moment in bookmark list
            issue ?: issueRepository.getIssueStubForArticle(articleStub.key)?.let { issue ->
                val moment = momentRepository.get(issue.issueKey)
                if (moment != null) {
                    contentService.downloadToCache(moment)
                }
            }

            if (article != null) {
                // Set bookmark
                addBookmarkAsync(article, fromRemote = true)
                bookmarkSynchronizationRepository.save(
                    newRemoteBookmarks, SynchronizeFromType.REMOTE, null, Date()
                )
            }
        }
    }

    /**
     * Find out if [localButNotRemoteBookmarks] needs to be synchronized or deleted locally.
     * Therefore we look at the timestamp of when we bookmarked it:
     */
    private suspend fun handleLocalButNotOnRemoteBookmarks(localButNotRemoteBookmarks: List<BookmarkRepresentation>) {
        localButNotRemoteBookmarks.forEach {
            val bookmarkSynchronization = bookmarkSynchronizationRepository.get(it.mediaSyncId)
            val originallyFromLocal = bookmarkSynchronization?.from == SynchronizeFromType.LOCAL
            val notYestSynchronized =  bookmarkSynchronization?.synchronizedTime == null
            // Check if it is not synchronized, then push it to remote:
            if (originallyFromLocal && notYestSynchronized) {
                pushLocalBookmarkToRemote(it)
                log.verbose("article ${it.mediaSyncId} pushed to remote.")
            } else {
                // Otherwise delete it locally:
                val article = articleRepository.getStubByMediaSyncId(it.mediaSyncId)
                if (article != null) {
                    removeBookmarkAsync(article, fromRemote = true)
                    log.verbose("article ${it.mediaSyncId} deleted locally as not on remote anymore.")
                }
            }
        }
    }

    private suspend fun deleteRemoteBookmark(articleMediaSyncId: Int) {
        try {
            val success = apiService.deleteRemoteBookmark(articleMediaSyncId)
            if (success) {
                bookmarkSynchronizationRepository.delete(articleMediaSyncId)
            }
        } catch (e: Exception) {
            bookmarkSynchronizationRepository.markAsLocallyChanged(articleMediaSyncId)
            log.warn("Could not synchronize deleted bookmark. ${e.message}")
        }
    }

    private suspend fun pushLocalBookmarkToRemote(bookmarkRepresentation: BookmarkRepresentation) {
        bookmarkSynchronizationRepository.save(bookmarkRepresentation, SynchronizeFromType.LOCAL)
        try {
            val success = apiService.saveSynchronizedBookmark(
                bookmarkRepresentation.mediaSyncId, bookmarkRepresentation.date
            )
            if (success) {
                bookmarkSynchronizationRepository.markAsSynchronized(bookmarkRepresentation)
            }
        } catch (e: Exception) {
            bookmarkSynchronizationRepository.markAsLocallyChanged(bookmarkRepresentation.mediaSyncId)
            log.warn("Could not synchronize bookmark. ${e.message}")
        }
    }

    private suspend fun handleRemoveBookmarkWhenSynchronizationIsEnabled(articleMediaSyncId: Int?) {
        if (articleMediaSyncId == null) {
            return
        }
        val bookmarksSynchronizationEnabled = generalDataStore.bookmarksSynchronizationEnabled.get()
        if (bookmarksSynchronizationEnabled) {
            deleteRemoteBookmark(articleMediaSyncId)
        }
    }

    private suspend fun handleSaveBookmarkWhenSynchronizationIsEnabled(
        articleMediaSyncId: Int?,
        articleDate: String,
    ) {
        if (articleMediaSyncId == null) {
            return
        }
        val bookmarksSynchronizationEnabled = generalDataStore.bookmarksSynchronizationEnabled.get()
        if (bookmarksSynchronizationEnabled) {
            val bookmarkRepresentation = BookmarkRepresentation(articleMediaSyncId, articleDate)
            pushLocalBookmarkToRemote(bookmarkRepresentation)
        }
    }

    // endregion
}
