package de.taz.app.android.audioPlayer

import android.content.Context
import de.taz.app.android.persistence.repository.ArticleRepository

class AudioPlayerItemInitHelper(private val applicationContext: Context) {

    private val articleRepository = ArticleRepository.getInstance(applicationContext)

    suspend fun initAudioPlayerItem(initItem: AudioPlayerItemInit): AudioPlayerItem {
        return when (initItem) {
            is IssueOfArticleInit -> initIssueOfArticleAudio(initItem)
            is IssueInit -> initIssueAudio(initItem)
            is ArticleInit -> initArticleAudio(initItem)
        }
    }

    private suspend fun initIssueOfArticleAudio(initItem: IssueOfArticleInit): IssueAudio {
        val articleStub = initItem.articleStub
        val issueStub = articleStub.getIssueStub(applicationContext)
            ?: throw AudioPlayerException.Generic("No issue found for $initItem")
        val articles = articleRepository.getArticleListForIssue(issueStub.issueKey)
        val articlesWithAudio = articles.filter { it.audio != null }
        val indexOfArticle =
            articlesWithAudio.indexOfFirst { it.key == articleStub.key }.coerceAtLeast(0)
        return IssueAudio(
            issueStub,
            articlesWithAudio,
            indexOfArticle,
            indexOfArticle,
        )
    }

    private suspend fun initIssueAudio(initItem: IssueInit): IssueAudio {
        val issueStub = initItem.issueStub
        val articles = articleRepository.getArticleListForIssue(issueStub.issueKey)
        val articlesWithAudio = articles.filter { it.audio != null }
        return IssueAudio(
            issueStub,
            articlesWithAudio,
            0,
            0,
        )
    }

    private suspend fun initArticleAudio(initItem: ArticleInit): ArticleAudio {
        val articleStub = initItem.articleStub
        val article = articleRepository.get(articleStub.articleFileName)
        if (article != null) {
            val sectionStub = article.getSectionStub(applicationContext)
            val issueStub = sectionStub?.getIssueStub(applicationContext)

            if (issueStub != null && article.audio != null) {
                return ArticleAudio(issueStub, article)
            } else {
                throw AudioPlayerException.Generic("Could not load audio data for the Article(key=${article.key})")
            }
        } else {
            throw AudioPlayerException.Generic("Could not load the full Article for ArticleStub(articleFileName=${articleStub.articleFileName})")
        }
    }
}