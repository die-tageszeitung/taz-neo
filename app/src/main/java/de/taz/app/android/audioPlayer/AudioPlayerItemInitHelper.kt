package de.taz.app.android.audioPlayer

import android.content.Context
import de.taz.app.android.BuildConfig
import de.taz.app.android.api.interfaces.SectionOperations
import de.taz.app.android.api.models.Audio
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.content.ContentService
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.StoragePathService

class AudioPlayerItemInitHelper(
    private val applicationContext: Context,
    private val uiStateHelper: UiStateHelper
) {
    private val contentService = ContentService.getInstance(applicationContext)
    private val articleRepository = ArticleRepository.getInstance(applicationContext)
    private val issueRepository = IssueRepository.getInstance(applicationContext)
    private val storagePathService = StoragePathService.getInstance(applicationContext)

    suspend fun initIssueOfArticleAudio(articleKey: String): List<AudioPlayerItem> {
        val issueStub = issueRepository.getIssueStubForArticle(articleKey)
            ?: throw AudioPlayerException.Generic("No issue found for $articleKey")
        val issueKey = issueStub.issueKey // small optimization to only create one IssueKey instance
        val articles = articleRepository.getArticleListForIssue(issueKey)
        val articlesWithAudio = articles.filter { it.audio != null }

        return articlesWithAudio.map {
            val audio = requireNotNull(it.audio)
            AudioPlayerItem(
                generateId(audio),
                audio,
                storagePathService.determineBaseUrl(it.audio.file, issueStub),
                uiStateHelper.articleAsAUiItem(it, issueKey),
                issueKey,
                it.key,
                type = AudioPlayerItem.Type.ARTICLE,
            )
        }
    }

    suspend fun initIssueAudio(issueStub: IssueStub): List<AudioPlayerItem> {
        val issueKey = issueStub.issueKey // small optimization to only create one IssueKey instance
        val articles = articleRepository.getArticleListForIssue(issueStub.issueKey)
        val articlesWithAudio = articles.filter { it.audio != null }
        return articlesWithAudio.map {
            val audio = requireNotNull(it.audio)
            AudioPlayerItem(
                generateId(audio),
                audio,
                storagePathService.determineBaseUrl(it.audio.file, issueStub),
                uiStateHelper.articleAsAUiItem(it, issueKey),
                issueKey,
                it.key,
                type = AudioPlayerItem.Type.ARTICLE,
            )
        }
    }

    suspend fun initArticleAudio(articleKey: String): List<AudioPlayerItem> {
        val article = articleRepository.get(articleKey)
        if (article != null) {
            val sectionStub = article.getSectionStub(applicationContext)
            val issueStub = sectionStub?.getIssueStub(applicationContext)

            if (issueStub != null && article.audio != null) {
                val issueKey = issueStub.issueKey
                val articleAudioItem = AudioPlayerItem(
                    generateId(article.audio),
                    article.audio,
                    storagePathService.determineBaseUrl(article.audio.file, issueStub),
                    uiStateHelper.articleAsAUiItem(article, issueKey),
                    issueKey,
                    article.key,
                    type = AudioPlayerItem.Type.ARTICLE,
                )
                return listOf(articleAudioItem)
            } else {
                throw AudioPlayerException.Generic("Could not load audio data for the Article(key=${article.key})")
            }
        } else {
            throw AudioPlayerException.Generic("Could not load the full Article for articleKey=${articleKey}")
        }
    }

    suspend fun initPagePodcast(issueStub: IssueStub, page: Page, audio: Audio): List<AudioPlayerItem> {
        val podcastAudioItem = AudioPlayerItem(
            generateId(audio),
            audio,
            storagePathService.determineBaseUrl(audio.file, issueStub),
            uiStateHelper.podcastAsUiItem(page),
            issueKey = null, // podcasts are not really associated with a single issue
            playableKey = null,
            type = AudioPlayerItem.Type.PODCAST,
        )
        return listOf(podcastAudioItem)
    }

    suspend fun initSectionPodcast(issueStub: IssueStub, section: SectionOperations, audio: Audio): List<AudioPlayerItem> {
        val podcastAudioItem = AudioPlayerItem(
            generateId(audio),
            audio,
            storagePathService.determineBaseUrl(audio.file, issueStub),
            uiStateHelper.podcastAsUiItem(section),
            issueKey = null, // podcasts are not really associated with a single issue
            playableKey = null,
            type = AudioPlayerItem.Type.PODCAST,
        )
        return listOf(podcastAudioItem)
    }

    suspend fun initSearchHitAudio(searchHit: SearchHit): List<AudioPlayerItem>{
        if (searchHit.audioFileName != null) {
            val issuePublication =
                IssuePublication(BuildConfig.DISPLAYED_FEED, searchHit.date)
            val issue = contentService.downloadMetadata(
                issuePublication,
                maxRetries = 2
            ) as? Issue
            val article = ArticleRepository.getInstance(applicationContext)
                .get(searchHit.articleFileName)
            val audio = requireNotNull(article?.audio)
            return listOf(
                AudioPlayerItem(
                    id = generateId(audio),
                    audio = audio,
                    baseUrl = searchHit.baseUrl,
                    uiItem = uiStateHelper.searchHitAsUiItem(searchHit, issue?.issueKey),
                    issueKey = issue?.issueKey,
                    playableKey = searchHit.audioPlayerPlayableKey,
                    searchHit = searchHit,
                    type = AudioPlayerItem.Type.SEARCH_HIT,
                )
            )
        } else {
            throw AudioPlayerException.Generic("SearchHit has no audio! SearchHit(articleFileName=${searchHit.articleFileName})")
        }
    }

    private fun generateId(audio: Audio): String {
        return "${audio.file.name}/${System.currentTimeMillis()}"
    }
}