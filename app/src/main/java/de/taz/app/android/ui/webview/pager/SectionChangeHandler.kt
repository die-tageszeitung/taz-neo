package de.taz.app.android.ui.webview.pager

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.R
import de.taz.app.android.monkey.getRecyclerView
import de.taz.app.android.util.Log

/**
 * This class indicates a section change when paging inside the [ArticlePagerFragment]:
 * 1. a block view is between articles of different sections
 * 2. the header is translated so it looks like it is "exchanged".
 *
 * This class has to be attached to a [ViewPager2] in onCreate/View
 * and must be set to null onDestroy/View to prevent memory leaks.
 */

class SectionChangeHandler(
    viewPager: ViewPager2,
    private val appBarLayout: AppBarLayout
) {
    private val log by Log

    private val recyclerView = viewPager.getRecyclerView()

    private val backgroundColor =
        ContextCompat.getColor(viewPager.context, R.color.article_section_divider_color)
    private val dividerWidth =
        viewPager.context.resources.getDimension(R.dimen.article_section_divider_width)
    private val dividerMargin =
        viewPager.context.resources.getDimension(R.dimen.article_section_divider_margin)
    private val sectionMargin = dividerMargin * 2f + dividerWidth

    private val divider: View = View(viewPager.context).apply {
        setBackgroundColor(backgroundColor)
        visibility = View.INVISIBLE
    }

    init {
        (viewPager.parent as? ViewGroup)?.let { parent ->
            val viewPagerPosition = parent.indexOfChild(viewPager)
            val layoutParams = ViewGroup.LayoutParams(dividerWidth.toInt(), MATCH_PARENT)
            parent.addView(divider, viewPagerPosition + 1, layoutParams)
        }
    }

    fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
        val adapter = recyclerView.adapter as? ArticlePagerAdapter

        if (layoutManager == null || adapter == null) {
            log.warn("Attached LinearLayoutManager and an Adapter are required.")
            return
        }

        val currentView = layoutManager.findViewByPosition(position)
        val nextView = layoutManager.findViewByPosition(position + 1)

        // Always reset the applied offsets and visibility
        divider.visibility = View.INVISIBLE
        currentView?.translationX = 0f
        nextView?.translationX = 0f

        if (currentView == null || nextView == null) {
            return
        }

        val currentItem = adapter.articlePagerItems[position]
        val nextItem = adapter.articlePagerItems[position + 1]

        if (currentItem is ArticlePagerItem.ArticleRepresentation && nextItem is ArticlePagerItem.Tom) {
            addOffsetTransitionBetweenPagerItems(
                positionOffsetPixels,
                positionOffset,
                currentView,
                nextView
            )
        } else if (currentItem is ArticlePagerItem.ArticleRepresentation && nextItem is ArticlePagerItem.ArticleRepresentation) {
            val currentSection = currentItem.art.sectionKey
            val nextSection = nextItem.art.sectionKey

            if (currentSection != nextSection) {
                addOffsetTransitionBetweenPagerItems(
                    positionOffsetPixels,
                    positionOffset,
                    currentView,
                    nextView
                )
            } else {
                // remove the translationX if we are not in between sections:
                appBarLayout.translationX = 0f
            }
        } else {
            // remove the translationX if we are not in between sections:
            appBarLayout.translationX = 0f
        }
    }

    private fun addOffsetTransitionBetweenPagerItems(
        positionOffsetPixels: Int,
        positionOffset: Float,
        currentView: View,
        nextView: View,
    ) {
        // The original article border is where both views would touch without the translation
        val borderOffsetFromParent = recyclerView.width - positionOffsetPixels

        val currentTransformOffset = -positionOffset
        currentView.translationX = currentTransformOffset * sectionMargin

        val nextTransformOffset = 1f - positionOffset
        nextView.translationX = nextTransformOffset * sectionMargin

        val dividerCenterTransformOffset =
            (currentTransformOffset + nextTransformOffset) / 2f
        val dividerCenterTranslationFromBorder =
            dividerCenterTransformOffset * sectionMargin
        divider.apply {
            translationX =
                borderOffsetFromParent + dividerCenterTranslationFromBorder - (divider.width / 2f)
            visibility = View.VISIBLE
        }
        // Add translation for the header on the right side or left side:
        if (positionOffset < 0.5f) {
            appBarLayout.translationX =
                -(recyclerView.width + dividerWidth) * positionOffset
        } else {
            appBarLayout.translationX =
                borderOffsetFromParent.toFloat() + dividerCenterTranslationFromBorder * nextTransformOffset
        }
    }

    fun activateScrollBar(position: Int, activate: Boolean = true) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager

        if (layoutManager == null) {
            log.warn("Attached LinearLayoutManager is required.")
            return
        }
        val previousView = layoutManager.findViewByPosition(position - 1)
        val currentView = layoutManager.findViewByPosition(position)
        val nextView = layoutManager.findViewByPosition(position + 1)

        previousView?.findViewById<NestedScrollView>(R.id.nested_scroll_view)?.isVerticalScrollBarEnabled = activate
        currentView?.findViewById<NestedScrollView>(R.id.nested_scroll_view)?.isVerticalScrollBarEnabled = activate
        nextView?.findViewById<NestedScrollView>(R.id.nested_scroll_view)?.isVerticalScrollBarEnabled = activate
    }
}