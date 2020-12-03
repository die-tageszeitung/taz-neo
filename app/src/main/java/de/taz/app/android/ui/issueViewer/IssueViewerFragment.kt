package de.taz.app.android.ui.issueViewer

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.*
import de.taz.app.android.api.models.*
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.monkey.observeDistinctIgnoreFirst
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.IssueLoaderFragment
import de.taz.app.android.ui.webview.ImprintWebViewFragment
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.ui.webview.pager.SectionPagerFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.*

class IssueViewerFragment :
    BaseViewModelFragment<IssueViewerViewModel>(R.layout.fragment_issue_content), BackFragment {

    override val viewModel: IssueViewerViewModel by lazy {
        ViewModelProvider(
            requireActivity(),
            SavedStateViewModelFactory(this.requireActivity().application, requireActivity())
        ).get(IssueViewerViewModel::class.java)
    }

    private val log by Log

    private lateinit var sectionPagerFragment: SectionPagerFragment
    private lateinit var articlePagerFragment: ArticlePagerFragment
    private lateinit var imprintFragment: ImprintWebViewFragment
    private lateinit var loaderFragment: IssueLoaderFragment
    private lateinit var dataService: DataService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sectionPagerFragment = SectionPagerFragment()
        articlePagerFragment = ArticlePagerFragment()
        imprintFragment = ImprintWebViewFragment()
        loaderFragment = IssueLoaderFragment()
        dataService = DataService.getInstance(requireContext().applicationContext)
        addFragment(sectionPagerFragment)
        addFragment(articlePagerFragment)
        addFragment(imprintFragment)
        addFragment(loaderFragment)
    }

    override fun onResume() {
        super.onResume()
        viewModel.activeDisplayMode.observeDistinct(this.viewLifecycleOwner) {
            setDisplayMode(it)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeAuthStatusAndChangeIssue()
    }

    private fun setDisplayMode(displayMode: IssueContentDisplayMode) {
        val transaction = childFragmentManager.beginTransaction()
        childFragmentManager.fragments.forEach {
            transaction.hide(it)
        }
        val fragmentClassToShow = when (displayMode) {
            IssueContentDisplayMode.Article -> ArticlePagerFragment::class.java
            IssueContentDisplayMode.Section -> SectionPagerFragment::class.java
            IssueContentDisplayMode.Imprint -> ImprintWebViewFragment::class.java
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
                .runOnCommit { runWhenAdded?.let { runWhenAdded() } }
                .commit()
        }
    }

    override fun onBackPressed(): Boolean {
        return childFragmentManager.fragments.lastOrNull { it.isVisible }?.let {
            when ((it as? BackFragment?)?.onBackPressed()) {
                true -> true
                false -> {
                    runIfNotNull(
                        it as? ArticlePagerFragment,
                        viewModel.issueKeyAndDisplayableKeyLiveData.value?.issueKey,
                        viewModel.lastSectionKey
                    ) { _, currentIssueKey, lastSectionKey ->
                        viewModel.setDisplayable(currentIssueKey, lastSectionKey)
                        true
                    } ?: false
                }
                null -> false
            }
        } ?: false
    }

    private fun observeAuthStatusAndChangeIssue() {
        val authHelper = AuthHelper.getInstance(context?.applicationContext)

        authHelper.authStatusLiveData.observeDistinctIgnoreFirst(viewLifecycleOwner) { authStatus ->
            val currentDisplayable = viewModel.currentDisplayable!!
            val issueKey = viewModel.issueKeyAndDisplayableKeyLiveData.value?.issueKey
            issueKey?.let {
                if (authStatus == AuthStatus.valid && issueKey.status == IssueStatus.public) {
                    log.verbose("Enter loading state")
                    viewModel.setDisplayable(null)
                    lifecycleScope.launch {
                        val issueStub =
                            withContext(Dispatchers.IO) { dataService.getIssueStub(issueKey) }
                        runIfNotNull(issueStub?.feedName, issueStub?.date) { feedName, date ->
                            CoroutineScope(Dispatchers.IO).launch {
                                dataService.getIssue(
                                    IssueKey(feedName, date, IssueStatus.regular),
                                    retryOnFailure = true
                                )?.let {
                                    dataService.ensureDownloaded(it)
                                    withContext(Dispatchers.Main) {
                                        log.verbose("Set new displayable")
                                        viewModel.setDisplayable(
                                            it.issueKey,
                                            currentDisplayable.replace(
                                                "public.",
                                                ""
                                            ),
                                            immediate = true
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}
