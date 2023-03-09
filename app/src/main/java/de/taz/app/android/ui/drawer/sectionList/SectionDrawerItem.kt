package de.taz.app.android.ui.drawer.sectionList

import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section

sealed class SectionDrawerItem {
    class Item(val article: Article) : SectionDrawerItem()
    class Header(val section: Section, var isExpanded: Boolean = false) : SectionDrawerItem()
}
