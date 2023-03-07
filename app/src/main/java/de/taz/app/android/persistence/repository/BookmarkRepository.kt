package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.withTransaction
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleBookmarkTime
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.util.SingletonHolder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import kotlin.collections.HashSet

class BookmarkRepository(
    applicationContext: Context,
    private val articleStubToArticle: suspend (ArticleStub) -> Article,
    private val coroutineScope: CoroutineScope
) : RepositoryBase(applicationContext) {
    private constructor(applicationContext: Context) : this(
        applicationContext,
        ArticleRepository.getInstance(applicationContext)::articleStubToArticle,
        CoroutineScope(Dispatchers.Default)
    )

    companion object : SingletonHolder<BookmarkRepository, Context>(::BookmarkRepository)

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

    fun toggleBookmarkAsync(articleFileName: String): Deferred<Boolean> {
        return coroutineScope.async { toggleBookmark(articleFileName) }
    }

    /**
     * Return [Boolean] new bookmark status of the article
     */
    suspend fun toggleBookmark(articleFileName: String): Boolean {
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
        } else {
            setArticleBookmark(articleFileName, Date())
        }
        triggerStateFlowUpdate()
        return !wasBookmarked
    }

    fun addBookmarkAsync(articleFileName: String): Deferred<Unit> {
        return coroutineScope.async { addBookmark(articleFileName) }
    }

    suspend fun addBookmark(articleFileName: String) {
        changeMutex.withLock {
            state.add(articleFileName)
        }
        setArticleBookmark(articleFileName, Date())
        triggerStateFlowUpdate()
    }

    fun removeBookmarkAsync(articleFileName: String): Deferred<Unit> {
        return coroutineScope.async { removeBookmark(articleFileName) }
    }

    suspend fun removeBookmark(articleFileName: String) {
        changeMutex.withLock {
            state.remove(articleFileName)
        }
        setArticleBookmark(articleFileName, null)
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
                .map { articleStubToArticle(it) }
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
                if (regularArticleFileName != publicArticleFileName) {
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
        appDatabase.articleDao().updateBookmarkedTime(ArticleBookmarkTime(articleFileName, date))
    }
}
