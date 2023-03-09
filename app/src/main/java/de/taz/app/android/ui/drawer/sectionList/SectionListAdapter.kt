package de.taz.app.android.ui.drawer.sectionList

import android.graphics.Typeface
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

private const val TYPE_HEADER = 0
private const val TYPE_ITEM = 1

class SectionListAdapter(
    private val onSectionClickListener: (Section) -> Unit,
    private val onArticleClick: (Article) -> Unit,
    private val onBookmarkClick: (Article) -> Unit,
    private val getBookmarkStateFlow: (String) -> Flow<Boolean>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var allOpened =  MutableStateFlow(false)
    var allClosed =  MutableStateFlow(true)
    var completeList: MutableList<SectionDrawerItem> = mutableListOf()
    var sectionDrawerItemList: MutableList<SectionDrawerItem> = mutableListOf()
        set(value) {
            field = value
            notifyDataSetChanged()
            updateAllOpenedOrClosed()
        }

    var typeface: Typeface? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> SectionHeaderViewHolder(parent, onSectionClickListener, ::toggleArticlesForSection)
            TYPE_ITEM -> ArticleItemViewHolder(parent, onArticleClick, onBookmarkClick, getBookmarkStateFlow)
            else -> error("Unknown viewType: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SectionHeaderViewHolder -> {
                if (position != RecyclerView.NO_POSITION) {
                    val headerItem = sectionDrawerItemList[position] as SectionDrawerItem.Header
                    holder.bind(headerItem, typeface)
                }
            }
            is ArticleItemViewHolder -> {
                holder.coroutineContext.cancelChildren()
                val article = sectionDrawerItemList[position] as SectionDrawerItem.Item
                holder.bind(article)
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is ArticleItemViewHolder -> {
                holder.coroutineContext.cancelChildren()
            }
        }
    }

    override fun getItemCount() = sectionDrawerItemList.size

    private fun indexOf(sectionFileName: String): Int {
        return sectionDrawerItemList.indexOfFirst {
            val headerItem = it as? SectionDrawerItem.Header
            headerItem?.section?.key == sectionFileName
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (sectionDrawerItemList[position]) {
            is SectionDrawerItem.Header -> TYPE_HEADER
            else -> TYPE_ITEM
        }
    }

    private fun toggleArticlesForSection(section: Section) {
        val index = indexOf(section.key)

        val indexOutOfBounds = index +1 >= sectionDrawerItemList.size
        if (!indexOutOfBounds && sectionDrawerItemList[index+1] is SectionDrawerItem.Item) {
            collapseArticlesForSection(section)
        } else {
            expandArticlesForSection(section)
        }
    }

    private fun collapseArticlesForSection(section: Section) {
        val index = indexOf(section.key)
        val indexOfFirstArticle = indexOf(section.key) + 1
        var amountCollapsed = 0

        var indexOutOfBounds = false
        while (!indexOutOfBounds && sectionDrawerItemList[indexOfFirstArticle] is SectionDrawerItem.Item) {
            sectionDrawerItemList.removeAt(indexOfFirstArticle)
            amountCollapsed++
            indexOutOfBounds = indexOfFirstArticle >= sectionDrawerItemList.size
        }
        updateAllOpenedOrClosed()
        sectionDrawerItemList[index] = SectionDrawerItem.Header(section, isExpanded = false)
        notifyItemChanged(index)
        notifyItemRangeRemoved(indexOfFirstArticle, amountCollapsed)
    }

    private fun expandArticlesForSection(section: Section) {
        val index = indexOf(section.key)
        val articlesToExpand = getArticlesToShow(section)
        sectionDrawerItemList.addAll(index+1, articlesToExpand)
        updateAllOpenedOrClosed()
        sectionDrawerItemList[index] = SectionDrawerItem.Header(section, isExpanded = true)
        notifyItemChanged(index)
        notifyItemRangeInserted(index+1, articlesToExpand.size)
    }

    private fun getArticlesToShow(section: Section): List<SectionDrawerItem> {
        val indexOfFirstArticle = completeList.indexOfFirst {
            val headerItem = it as? SectionDrawerItem.Header
            headerItem?.section?.key == section.key
        } + 1
        var amountToExpand = 0
        var indexOutOfBounds = false
        while (!indexOutOfBounds && completeList[indexOfFirstArticle + amountToExpand] is SectionDrawerItem.Item) {
            amountToExpand++
            indexOutOfBounds = indexOfFirstArticle + amountToExpand >= completeList.size
        }
        return completeList.subList(indexOfFirstArticle, indexOfFirstArticle + amountToExpand)
    }

    /**
     * This function compares the [sectionDrawerItemList] with the [completeList] to determine and
     * set the values [allOpened] and [allClosed] accordingly.
     */
    private fun updateAllOpenedOrClosed() {
        allOpened.value = sectionDrawerItemList.size == completeList.size
        val onlySectionsList = completeList.filterIsInstance<SectionDrawerItem.Header>()
        allClosed.value = sectionDrawerItemList.size == onlySectionsList.size
        if (allOpened.value) {
            sectionDrawerItemList.map {
                if (it is SectionDrawerItem.Header) {
                    it.isExpanded = true
                }
            }
        } else if (allClosed.value) {
            sectionDrawerItemList.map {
                if (it is SectionDrawerItem.Header) {
                    it.isExpanded = false
                }
            }
        }
    }
}
