package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BaseMainFragment
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.webview.ArticleWebViewFragment
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_article_pager.*

class ArticlePagerFragment : BaseMainFragment<ArticlePagerPresenter>(),
    ArticlePagerContract.View,
    BackFragment {

    override val presenter = ArticlePagerPresenter()

    val log by Log

    private var initialSection: Section? = null
    private var initialArticlePosition: Int? = null


    companion object {
        fun createInstance(section: Section, initialArticle: Article): ArticlePagerFragment {
            // FIXME: think about using the Bundle with a section id and getting the data from the viewmodel directly
            val fragment = ArticlePagerFragment()
            fragment.initialSection = section
            fragment.initialArticlePosition = section.articleList.indexOf(initialArticle)

            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        log.debug("onViewCreated: $view $savedInstanceState")

        // Attach the presenter to this view and ensure its datamodel is created and bound to this fragments lifecycle
        presenter.attach(this)

        // Ensure initial fragment states are copied to the model via the presenter
        initialSection?.let { presenter.setSection(it) }
        initialArticlePosition?.let { presenter.setCurrrentPosition(it) }

        // Initialize the presenter and let it call setSection on this fragment to render the pager
        presenter.onViewCreated(savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        article_view_pager.adapter = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_article_pager, container, false)
    }

    // FIXME: would love to register on main instead of implementing this via typechecking
    // This would also allow us to stack back handlers: for example while drawer is open its back handler is active,
    // when it is unregistered the previous callback handler will become active again.
    override fun onBackPressed(): Boolean {
        presenter.onBackPressed()
        return true
    }

    override fun setSection(section: Section, currentPosition: Int) {
        article_view_pager.apply {
            adapter = ArticlePagerAdapter(
                section,
                childFragmentManager
            )
            currentItem = currentPosition
            addOnPageChangeListener(pageChangeListener)
        }
    }

    private val pageChangeListener = object : ViewPager.SimpleOnPageChangeListener() {
        override fun onPageSelected(position: Int) {
            this@ArticlePagerFragment.presenter.setCurrrentPosition(position)
        }
    }

    private class ArticlePagerAdapter(
        private val section: Section,
        fragmentManager: FragmentManager
    ) : FragmentPagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        val log by Log

        override fun getItem(position: Int): Fragment {
            // FIXME: out of bounds errors?!
            val article = section.articleList[position]
            return ArticleWebViewFragment.createInstance(
                section.articleList[position]
            )
        }

        override fun getCount(): Int = section.articleList.size
    }
}