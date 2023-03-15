package de.taz.app.android.ui.drawer.sectionList

import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section

sealed class SectionDrawerItem {
    class Item(val article: Article) : SectionDrawerItem()
    data class Header(val section: Section, val isExpanded: Boolean = false) : SectionDrawerItem()
}
