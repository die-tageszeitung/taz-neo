package de.taz.app.android.persistence.repository

import android.content.Context
import androidx.room.withTransaction
import de.taz.app.android.api.models.AudioPlayerItemStub
import de.taz.app.android.audioPlayer.AudioPlayerItem
import de.taz.app.android.audioPlayer.Playlist
import de.taz.app.android.audioPlayer.UiStateHelper
import de.taz.app.android.dataStore.AudioPlayerDataStore
import de.taz.app.android.util.SingletonHolder
import de.taz.app.android.util.runIfNotNull

class PlaylistRepository private constructor(applicationContext: Context) :
    RepositoryBase(applicationContext) {

    companion object : SingletonHolder<PlaylistRepository, Context>(::PlaylistRepository)

    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val audioPlayerDataStore = AudioPlayerDataStore.getInstance(applicationContext)
    private val audioRepository = AudioRepository.getInstance(applicationContext)
    private val uiStateHelper = UiStateHelper(applicationContext)

    suspend fun sync(playlist: Playlist) {
        audioPlayerDataStore.playlistCurrent.set(playlist.currentItemIdx)
        return appDatabase.withTransaction {
            appDatabase.audioPlayerItemsDao()
                .deleteAllBut(playlist.items.map { AudioPlayerItemStub(it).audioPlayerItemId })
            appDatabase.audioPlayerItemsDao()
                .insertOrReplace(playlist.items.map { AudioPlayerItemStub(it) })
        }
    }

    suspend fun get(): Playlist {
        val itemStubs = appDatabase.audioPlayerItemsDao().getAll()
        val current = audioPlayerDataStore.playlistCurrent.get()
       return Playlist(current, mapStubToAudioPlayerItem(itemStubs))
    }

    private suspend fun mapStubToAudioPlayerItem(stubs: List<AudioPlayerItemStub>): List<AudioPlayerItem> {
        val list = stubs.mapNotNull {
            when (it.audioPlayerItemType) {
                AudioPlayerItem.Type.PODCAST -> mapPodcastAudioPlayerItem(it)
                AudioPlayerItem.Type.ARTICLE, AudioPlayerItem.Type.DISCLAIMER -> mapArticleAudioPlayerItem(it)
                AudioPlayerItem.Type.SEARCH_HIT -> mapSearchHitAudioPlayerItem(it)
            }
        }
        return list
    }

    private suspend fun mapArticleAudioPlayerItem(stub: AudioPlayerItemStub): AudioPlayerItem? {
        val audio = audioRepository.get(stub.audioFileName) ?: return null
        val key = stub.playableKey ?: return null
        val article = articleRepository.get(key) ?: return null
        val issueKey = runIfNotNull(
            stub.issueFeedName, stub.issueDate, stub.issueStatus
        ) { feed, date, status ->
            IssueKey(feed, date, status)
        } ?: return null

        return AudioPlayerItem(
            id = stub.audioPlayerItemId,
            audio = audio,
            baseUrl = stub.baseUrl,
            uiItem = uiStateHelper.articleAsAUiItem(article, issueKey),
            issueKey = issueKey,
            playableKey = key,
            searchHit = null,
            type = stub.audioPlayerItemType,
        )
    }

    private suspend fun mapPodcastAudioPlayerItem(stub: AudioPlayerItemStub): AudioPlayerItem? {
        val audio = audioRepository.get(stub.audioFileName) ?: return null
        val uiItem = AudioPlayerItem.UiItem(
            title = stub.uiTitle,
            author = null,
            coverImageUri = null,
            coverImageGlidePath = stub.uiCoverImageGlidePath,
            openItemSpec = null,
            type = AudioPlayerItem.Type.PODCAST,
        )

        return AudioPlayerItem(
            id = stub.audioPlayerItemId,
            audio = audio,
            baseUrl = stub.baseUrl,
            uiItem = uiItem,
            issueKey = null,
            playableKey = null,
            searchHit = null,
            type = stub.audioPlayerItemType,
        )
    }

    private suspend fun mapSearchHitAudioPlayerItem(stub: AudioPlayerItemStub): AudioPlayerItem? {
        val audio = audioRepository.get(stub.audioFileName) ?: return null
        val playableKey = stub.playableKey ?: return null

        val uiItem = AudioPlayerItem.UiItem(
            title = stub.uiTitle,
            author = stub.uiAuthor,
            coverImageUri = null,
            coverImageGlidePath = stub.uiCoverImageGlidePath,
            openItemSpec = null,
            type = AudioPlayerItem.Type.SEARCH_HIT,
        )

        return AudioPlayerItem(
            id = stub.audioPlayerItemId,
            audio = audio,
            baseUrl = stub.baseUrl,
            uiItem = uiItem,
            issueKey = null,
            playableKey = playableKey,
            searchHit = null,
            type = stub.audioPlayerItemType,
        )
    }
}