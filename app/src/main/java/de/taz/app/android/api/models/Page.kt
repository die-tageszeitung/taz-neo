package de.taz.app.android.api.models

import de.taz.app.android.api.interfaces.CacheableDownload


data class Page (
    val pagePdf: FileEntry,
    val title: String? = null,
    val pagina: String? = null,
    val type: PageType? = null,
    val frameList: List<Frame>? = null
) : CacheableDownload {
    override fun getAllFileNames(): List<String> {
        return listOf(pagePdf.name)
    }
}

enum class PageType {
    left,
    right,
    panorama
}
