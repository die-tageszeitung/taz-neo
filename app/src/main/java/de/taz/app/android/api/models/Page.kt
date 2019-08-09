package de.taz.app.android.api.models


data class Page (
    val pagePdf: FileEntry,
    val title: String? = null,
    val pagina: String? = null,
    val type: PageType? = null,
    val frameList: List<Frame>? = null
)

enum class PageType {
    left,
    right,
    panorama
}
