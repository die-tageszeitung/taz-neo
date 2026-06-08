package de.taz.app.android.monkey

fun String.isArticleKey(): Boolean {
    return endsWith(".html") && startsWith("art")
}

fun String.isSectionKey(): Boolean {
    return endsWith(".html") && startsWith("section.")
}

fun String.isPageKey(): Boolean {
    return endsWith(".pdf") && startsWith("s")
}

fun String.isPublicArticle(): Boolean {
    return endsWith("public.html") && startsWith("art")
}