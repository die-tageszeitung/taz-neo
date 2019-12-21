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
import de.taz.app.android.monkey.reduceDragSensitivity
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.StableIdProvider
import de.taz.app.android.util.StableIdViewModel
import kotlinx.android.synthetic.main.fragment_webview_pager.*

const val ARTICLE_POSITION = "position"

class ArticlePagerFragment : BaseMainFragment<ArticlePagerPresenter>(),
    ArticlePagerContract.View, BackFragment {

    override val presenter = ArticlePagerPresenter()

    val log by Log

    private var initialArticle: Article? = null

    private var stableIdProvider: StableIdProvider? = null
    private var articlePagerAdapter: ArticlePagerAdapter? = null

    private var currentPosition: Int? = null

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

        webview_pager_viewpager.reduceDragSensitivity()
        // Initialize the presenter and let it call this fragment to render the pager
        presenter.onViewCreated(savedInstanceState)

        stableIdProvider = ViewModelProviders.of(this).get(StableIdViewModel::class.java).also {
            articlePagerAdapter = ArticlePagerAdapter(this, it)
        }

        setupViewPager()

        if (savedInstanceState?.containsKey(ARTICLE_POSITION) == true) {
            currentPosition = savedInstanceState.getInt(ARTICLE_POSITION)
        }

        currentPosition?.let {
            presenter.setCurrentPosition(it)
            webview_pager_viewpager.currentItem = it
        }

    }

    override fun persistPosition(position: Int) {
        currentPosition = position
    }

    override fun onSaveInstanceState(outState: Bundle) {
        currentPosition?.let {
            outState.putInt(ARTICLE_POSITION, it)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        webview_pager_viewpager?.adapter = null
        super.onDestroyView()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview_pager, container, false)
    }

    override fun onBackPressed(): Boolean {
        getCurrentFragment()?.let {
            if (it.onBackPressed()) return true
        }
        presenter.onBackPressed()
        return true
    }

    private fun getCurrentFragment(): ArticleWebViewFragment? {
        return childFragmentManager.fragments.firstOrNull {
            (it as? ArticleWebViewFragment)?.let { fragment ->
                return@firstOrNull fragment.article == articlePagerAdapter?.getCurrentArticle()
            }
            return@firstOrNull false
        } as? ArticleWebViewFragment
    }

    private fun setupViewPager() {
        webview_pager_viewpager?.apply {
            adapter = articlePagerAdapter
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 1
            registerOnPageChangeCallback(pageChangeListener)
        }
    }

    private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            this@ArticlePagerFragment.presenter.setCurrentPosition(position)
        }
    }

    override fun setArticles(articles: List<Article>, currentPosition: Int) {
        webview_pager_viewpager.apply {
            (adapter as ArticlePagerAdapter?)?.submitList(articles)
            setCurrentItem(currentPosition, false)
        }
    }

    private inner class ArticlePagerAdapter(
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

        fun getCurrentArticle(): Article {
            return articles[webview_pager_viewpager.currentItem]
        }
    }
}