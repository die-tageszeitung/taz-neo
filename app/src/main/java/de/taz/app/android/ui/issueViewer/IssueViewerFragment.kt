package de.taz.app.android.ui.issueViewer

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import de.taz.app.android.*
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentIssueContentBinding
import de.taz.app.android.monkey.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.KeepScreenOnHelper
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.IssueLoaderFragment
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.ui.webview.pager.SectionPagerFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.*

/**
 * This Fragment is used by [IssueViewerWrapperFragment]
 * Show an Issue with sections and articles in their respective pager fragments
 *
 * Additional fragments are loaded to ensure the transitions are smooth.
 *
 * [sectionPagerFragment] is used to show the sections of the issue
 * [articlePagerFragment] is used to show the articles of the issue
 * [loaderFragment] shows the initial loading screen
 *
 * TODO Hopefully we can merge this with the [IssueViewerWrapperFragment]
 */
class IssueViewerFragment :
    BaseViewModelFragment<IssueViewerViewModel, FragmentIssueContentBinding>(), BackFragment {

    override val viewModel: IssueViewerViewModel by activityViewModels()

    private val log by Log

    private lateinit var bookmarkRepository: BookmarkRepository
    private lateinit var sectionRepository: SectionRepository
    private lateinit var tazApiCssDataStore: TazApiCssDataStore

    override fun onAttach(context: Context) {
        super.onAttach(context)
        bookmarkRepository = BookmarkRepository.getInstance(requireContext().applicationContext)
        sectionRepository = SectionRepository.getInstance(requireContext().applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(requireContext().applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            // reset viewModel when creating anew
            viewModel.setDisplayable(null)

            addFragment(SectionPagerFragment())
            addFragment(ArticlePagerFragment())
            addFragment(IssueLoaderFragment())
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.activeDisplayModeFlow.collect {
                        setDisplayMode(it)
                    }
                }

                launch {
                    tazApiCssDataStore.keepScreenOn.asFlow().collect {
                        KeepScreenOnHelper.toggleScreenOn(it, activity)
                    }
                }
            }

            bookmarkRepository.checkForSynchronizedBookmarksIfEnabled()
        }
    }

    private fun setDisplayMode(displayMode: IssueContentDisplayMode) {
        val transaction = childFragmentManager.beginTransaction()
        childFragmentManager.fragments.forEach {
            transaction.hide(it)
        }
        val fragmentClassToShow = when (displayMode) {
            IssueContentDisplayMode.Article -> ArticlePagerFragment::class.java
            IssueContentDisplayMode.Section -> SectionPagerFragment::class.java
            IssueContentDisplayMode.Loading -> IssueLoaderFragment::class.java
        }
        childFragmentManager.findFragmentByTag(fragmentClassToShow.toString())?.let {
            log.debug("Show $fragmentClassToShow")
            transaction.show(it).commit()
        }
    }

    private fun addFragment(fragment: Fragment, runWhenAdded: (() -> Unit)? = null) {
        val fragmentClass = fragment::class.java.toString()
        if (childFragmentManager.findFragmentByTag(fragmentClass) == null) {
            childFragmentManager.beginTransaction()
                .add(
                    R.id.fragment_issue_content_container, fragment, fragmentClass
                )
                .hide(fragment)
                .runOnCommit { runWhenAdded?.invoke() }
                .commit()
        }
    }

    override fun onBackPressed(): Boolean {
        return childFragmentManager.fragments.lastOrNull { it.isVisible }?.let {
            when ((it as? BackFragment?)?.onBackPressed()) {
                true -> true
                false -> {
                    val lastSectionKey = viewModel.lastSectionKey
                        ?: viewModel.currentDisplayable?.let { displayableKey ->
                            if (displayableKey.startsWith("art")) {
                                runBlocking {
                                    sectionRepository.getSectionStubForArticle(
                                        displayableKey
                                    )?.key
                                }
                            } else {
                                null
                            }
                        }
                    runIfNotNull(
                        viewModel.issueKeyAndDisplayableKeyFlow.value?.issueKey,
                        lastSectionKey
                    ) { currentIssueKey, displayableKey ->
                        lifecycleScope.launch {
                            viewModel.setDisplayable(
                                IssueKeyWithDisplayableKey(
                                    currentIssueKey,
                                    displayableKey
                                )
                            )
                        }
                        true
                    } ?: false
                }

                null -> false
            }
        } ?: false
    }

}
