package de.taz.app.android.ui.webview.pager

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.monkey.getRecyclerView
import de.taz.app.android.util.Log

/**
 * This class has to be attached to a [ViewPager2] in onCreate/View
 * and must be set to null onDestroy/View to prevent memory leaks
 */
class SectionDividerTransformer(
    viewPager: ViewPager2,
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
            parent.addView(divider, dividerWidth.toInt(), MATCH_PARENT)
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

        val (_, currentSection) = adapter.articleStubsWithSectionKey[position]
        val (_, nextSection) = adapter.articleStubsWithSectionKey[position + 1]

        if (currentSection != nextSection) {
            // The original article border is where both views would touch without the translation
            val borderOffsetFromParent = recyclerView.width - positionOffsetPixels

            val currentTransformOffset = -positionOffset
            currentView.translationX = currentTransformOffset * sectionMargin

            val nextTransformOffset = 1f - positionOffset
            nextView.translationX = nextTransformOffset * sectionMargin

            val dividerCenterTransformOffset = (currentTransformOffset + nextTransformOffset) / 2f
            val dividerCenterTranslationFromBorder = dividerCenterTransformOffset * sectionMargin
            divider.apply {
                translationX =
                    borderOffsetFromParent + dividerCenterTranslationFromBorder - (divider.width / 2f)
                visibility = View.VISIBLE
            }
        }
    }
}