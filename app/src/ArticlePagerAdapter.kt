package de.taz.app.android.ui.webview.pager

import android.annotation.SuppressLint
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.ArticleStubWithSectionKey
import de.taz.app.android.api.models.TomAtTheEndFragment
import de.taz.app.android.ui.webview.ArticleWebViewFragment

class ArticlePagerAdapter(
    articleList: List<ArticleStubWithSectionKey>,
    fragment: Fragment,
) : FragmentStateAdapter(fragment) {

    private val articleRepresentations =
        articleList.map { ArticlePagerItem.ArticleRepresentation(it) }

    @SuppressLint("DiscouragedApi") // as we do not have the identifiers for some variants
    private val tomItems: List<ArticlePagerItem.Tom> = run {
        val context = fragment.context ?: return@run emptyList()

        val tomDrawableNames = listOf(
            "tom_01",
            "tom_02",
            "tom_03",
            "tom_04",
            "tom_05",
            "tom_06",
            "tom_07",
            "tom_08",
            "tom_09",
            "tom_10",
            "tom_11",
            "tom_12",
        )

        tomDrawableNames
            .map {
                context.resources.getIdentifier(it, "drawable", context.packageName)
            }
            .filter { it != 0 }
            .map {
                ArticlePagerItem.Tom(it)
            }
    }

    val articleStubs: List<ArticleStub> = articleRepresentations.map { it.art.articleStub }

    val articlePagerItems = articleRepresentations + tomItems

    override fun createFragment(position: Int): Fragment {
        return if (articlePagerItems[position] is ArticlePagerItem.ArticleRepresentation) {
            val articleStub =
                (articlePagerItems[position] as ArticlePagerItem.ArticleRepresentation).art.articleStub
            ArticleWebViewFragment.newInstance(articleStub)
        } else {
            val tom = articlePagerItems[position] as ArticlePagerItem.Tom
            TomAtTheEndFragment.newInstance(tom.tomResId)
        }
    }

    override fun getItemCount(): Int = articlePagerItems.size
}