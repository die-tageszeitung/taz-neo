package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.StableIdProvider
import de.taz.app.android.util.StableIdViewModel
import kotlinx.android.synthetic.main.fragment_webview_pager.*

class ArticlePagerFragment : BaseMainFragment<ArticlePagerPresenter>(),
    ArticlePagerContract.View,
    BackFragment {

    override val presenter = ArticlePagerPresenter()

    val log by Log

    private var initialArticle: Article? = null

    companion object {
        fun createInstance(initialArticle: Article): ArticlePagerFragment {
            // FIXME: think about using the Bundle with a  id and getting the data from the viewmodel directly
            val fragment = ArticlePagerFragment()
            fragment.initialArticle = initialArticle
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        log.debug("onViewCreated: $view $savedInstanceState")

        // Attach the presenter to this view and ensure its datamodel is created and bound to this fragments lifecycle
        presenter.attach(this)

        // Ensure initial fragment states are copied to the model via the presenter
        initialArticle?.let { presenter.setInitialArticle(it) }

        // Initialize the presenter and let it call this fragment to render the pager
        presenter.onViewCreated(savedInstanceState)

        setupViewPager()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webview_pager_viewpager.adapter = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview_pager, container, false)
    }

    // FIXME: would love to register on main instead of implementing this via typechecking
    // This would also allow us to stack back handlers: for example while drawer is open its back handler is active,
    // when it is unregistered the previous callback handler will become active again.
    override fun onBackPressed(): Boolean {
        presenter.onBackPressed()
        return true
    }

    private fun setupViewPager() {
        val stableIdProvider = ViewModelProviders.of(this).get(StableIdViewModel::class.java)
        val sectionAdapter = ArticlePagerAdapter(this, stableIdProvider)
        webview_pager_viewpager.apply {
            adapter = sectionAdapter
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 1
            registerOnPageChangeCallback(pageChangeListener)
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            this@ArticlePagerFragment.presenter.setCurrrentPosition(position)
        }
    }

    override fun setArticles(articles: List<Article>, currentPosition: Int) {
        webview_pager_viewpager.apply {
            (adapter as ArticlePagerAdapter?)?.submitList(articles)
            setCurrentItem(currentPosition, false)
        }
    }

    private class ArticlePagerAdapter(
        fragment: Fragment,
        private val stableIdProvider: StableIdProvider
    ) : FragmentStateAdapter(fragment) {
        private var articles = emptyList<Article>()

        override fun createFragment(position: Int): Fragment {
            val article = articles[position]
            return ArticleWebViewFragment.createInstance(article)
        }

        override fun getItemCount(): Int = articles.size

        override fun getItemId(position: Int): Long {
            val filename = articles[position].articleFileName
            return stableIdProvider.getId(filename)
        }

        fun submitList(newArticles: List<Article>) {
            articles = newArticles
            notifyDataSetChanged()
        }
    }
}