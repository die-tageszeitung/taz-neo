package de.taz.app.android.persistence.cache

import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.util.Log
import de.taz.app.android.util.SingletonHolder
import java.util.LinkedList

private const val CACHE_SIZE = 5

class IssueInMemoryCache(unused: Any) {

    companion object : SingletonHolder<IssueInMemoryCache, Any>(::IssueInMemoryCache)
    private val log by Log

    private val cache = LinkedList<Issue>()

    fun get(issueKey: IssueKey): Issue? {
        val index = cache.indexOfFirst { it.issueKey == issueKey }
        if (index < 0) {
            return null
        }

        // This is some LRU cache, so we move that item to the top
        val issue = cache.removeAt(index)
        cache.add(issue)

        log.verbose("Cache hit for issue: $issueKey")
        return issue
    }

    fun invalidate(issueKey: IssueKey) {
        val index = cache.indexOfFirst { it.issueKey == issueKey }
        if (index >= 0) {
            log.verbose("Invalidated issue: $issueKey")
            cache.removeAt(index)
        }
    }

    fun invalidate(issuePublication: IssuePublication) {
        val removed = cache.removeIf {
            IssuePublication(it.issueKey) == issuePublication
        }
        if (removed) {
            log.verbose("Invalidated all issues of publication: $issuePublication")
        }
    }

    fun invalidateByDate(issueDate: String) {
        val removed = cache.removeIf {
            it.date == issueDate
        }
        if (removed) {
            log.verbose("Invalidated all issues with date: $issueDate")
        }
    }

    fun update(issueStub: IssueStub) {
        val index = cache.indexOfFirst { it.issueKey == issueStub.issueKey }
        if (index >= 0) {
            val issue = cache[index]
            cache[index] = issue.copyWithMetadata(issueStub)
            log.verbose("Updated issue: ${issueStub.issueKey}")
        }
    }

    /**
     * Attention: this is a relative expensive operation as we have to iterate all sections/articles
     */
    fun updateArticle(articleStub: ArticleStub) {
        val issuePublication = IssuePublication(articleStub)

        // FIXME: could be multiple articles!
        val index = cache.indexOfFirst {
            IssuePublication(it.issueKey) == issuePublication
        }

        if (index >= 0) {
            val issue = cache[index]
            var found: Pair<Int, Int>? = null

            issue.sectionList.forEachIndexed { sectionIndex, section ->
                section.articleList.withIndex().find {
                    it.value.key == articleStub.key
                }?.let {
                    found = sectionIndex to it.index
                }
            }

            if (found != null) {
                val (sectionIndex, articleIndex) = found!!
                val mutableSectionList = issue.sectionList.toMutableList()
                val section = mutableSectionList[sectionIndex]

                val mutableArticleList = section.articleList.toMutableList()
                val article = mutableArticleList[articleIndex]

                mutableArticleList[articleIndex] = article.copyWithMetadata(articleStub)
                mutableSectionList[sectionIndex] = section.copy(articleList = mutableArticleList)

                cache[index] = issue.copy(sectionList = mutableSectionList)

                log.verbose("Updated articles on $issuePublication")
            }
        }
    }

    fun add(issue: Issue) {
        // Remove if previously stored
        invalidate(issue.issueKey)
        cache.add(issue)

        log.verbose("Added issue: ${issue.issueKey}")

        // Evict the oldest entry if the cache size is hit
        if (cache.size > CACHE_SIZE) {
            val evictedIssue = cache.pop()
            log.verbose("Evicted oldest issue: ${evictedIssue.issueKey}")
        }
    }
}