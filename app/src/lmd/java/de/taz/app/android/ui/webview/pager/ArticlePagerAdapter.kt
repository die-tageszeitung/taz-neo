package de.taz.app.android.ui.webview.pager

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import de.taz.app.android.api.models.ArticleWithSectionKey
import de.taz.app.android.ui.webview.ArticleWebViewFragment

class ArticlePagerAdapter(
    val articleList: List<ArticleWithSectionKey>,
    fragment: Fragment,
) : FragmentStateAdapter(fragment) {

    // TODO check why not needed in taz but in LMD
    val articlePagerItems = articleList.map { ArticlePagerItem.ArticleRepresentation(it) }

    override fun createFragment(position: Int): Fragment {
        val article = articleList[position]
        return ArticleWebViewFragment.newInstance(article.article)
    }

    override fun getItemCount(): Int = articleList.size
}