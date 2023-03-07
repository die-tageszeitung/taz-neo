package de.taz.app.android.util

object ArticleName {
    private val articleFilenamePattern = """(art\d+)\.[a-zA-Z\d\.]+""".toRegex()
    fun fromArticleFileName(articleFileName: String): String {
        val match = articleFilenamePattern.matchEntire(articleFileName)
        requireNotNull(match) { "ArticleFileName $articleFileName does not match a known article filename pattern"}
        return match.groupValues[1]
    }
}