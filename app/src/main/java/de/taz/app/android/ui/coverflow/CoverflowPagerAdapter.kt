package de.taz.app.android.ui.coverflow

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.core.view.setPadding
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import de.taz.app.android.R
import de.taz.app.android.api.models.Feed
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.ui.archive.item.ArchiveItemView
import kotlinx.coroutines.launch


class CoverflowPagerAdapter(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val issues: List<IssueStub>,
    private val feed: Feed?
): PagerAdapter() {
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val view = getViewAtPosition(position)
        container.addView(view)
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, view: Any) {
        container.removeView(view as View)
    }

    override fun getCount(): Int {
        return issues.size
    }

    /**
     * Used to determine whether the page view is associated with object key returned by instantiateItem.
     * Since here view only is the key we return view==object
     *
     */
    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return `object` === view
    }

    private fun getViewAtPosition(position: Int): ArchiveItemView {
        val issue = issues[position]
        val view = ArchiveItemView(context)
        view.clipChildren = false
        lifecycleOwner.lifecycleScope.launch {
            view.presenter.setIssue(issue, feed)
        }

        return view
    }
}