package de.taz.app.android.ui.webview.pager

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.ArticleStubWithSectionKey
import de.taz.app.android.ui.webview.ArticleWebViewFragment

class ArticlePagerAdapter(
    val articleStubsWithSectionKey: List<ArticleStubWithSectionKey>,
    fragment: Fragment
): FragmentStateAdapter(fragment) {

    val articleStubs: List<ArticleStub> = articleStubsWithSectionKey.map { it.articleStub }

    override fun createFragment(position: Int): Fragment {
        val article = articleStubs[position]
        return ArticleWebViewFragment.newInstance(article.articleFileName)
    }


    override fun getItemId(position: Int): Long {
        return articleStubs[position].key.hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return articleStubs.any { itemId == it.key.hashCode().toLong() }
    }

    fun getPositionOfArticle(name: String): Int {
        return articleStubs.indexOfFirst {
            it.articleFileName == name
        }
    }

    override fun getItemCount(): Int = articleStubs.size
}