package de.taz.app.android.ui.webview.pager

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.ArticleStubWithSectionKey
import de.taz.app.android.ui.webview.ArticleWebViewFragment

class ArticlePagerAdapter(
    articleList: List<ArticleStubWithSectionKey>,
    fragment: Fragment,
) : FragmentStateAdapter(fragment) {

    private val articleRepresentations =
        articleList.map { ArticlePagerItem.ArticleRepresentation(it) }

    val articleStubs: List<ArticleStub> = articleRepresentations.map { it.art.articleStub }

    val articlePagerItems = articleRepresentations

    override fun createFragment(position: Int): Fragment {
        val articleStub = articlePagerItems[position].art.articleStub
        return ArticleWebViewFragment.newInstance(articleStub)
    }

    override fun getItemCount(): Int = articlePagerItems.size
}