package de.taz.app.android.ui.webview.pager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.monkey.observeDistinctUntil
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Exception
import java.lang.IllegalStateException

const val ISSUE_DATE = "issueDate"
const val ISSUE_FEED = "issueFeed"
const val ISSUE_STATUS = "issueStatus"
const val DISPLAYABLE_KEY = "webViewDisplayableKey"

class IssueContentFragment :
    BaseViewModelFragment<IssueContentViewModel>(R.layout.fragment_issue_content), BackFragment {

    override val enableSideBar: Boolean = true

    private var displayableKey: String? = null
    private var issueFeedName: String? = null
    private var issueDate: String? = null
    private var issueStatus: IssueStatus? = null

    private var sectionPagerFragment = SectionPagerFragment()
    private var articlePagerFragment = ArticlePagerFragment()

    companion object {
        fun createInstance(issueOperations: IssueOperations): IssueContentFragment {
            val fragment = IssueContentFragment()
            fragment.issueFeedName = issueOperations.feedName
            fragment.issueDate = issueOperations.date
            fragment.issueStatus = issueOperations.status
            return fragment
        }

        fun createInstance(displayableName: String): IssueContentFragment {
            val fragment = IssueContentFragment()
            fragment.displayableKey = displayableName
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.apply {
            issueDate = getString(ISSUE_DATE)
            issueFeedName = getString(ISSUE_FEED)
            try {
                issueStatus = getString(ISSUE_STATUS)?.let { IssueStatus.valueOf(it) }
            } catch (e: IllegalArgumentException) {
                // do nothing issueStatus is null
            }
            displayableKey = getString(DISPLAYABLE_KEY)
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addFragment(sectionPagerFragment)
        addFragment(articlePagerFragment)

        displayableKey?.let {
            getIssueOperationByDisplayableKey()
        }

        runIfNotNull(issueFeedName, issueDate, issueStatus) { feed, date, status ->
            getIssueStub(feed, date, status)
        }

        viewModel.issueOperationsLiveData.observeDistinctUntil(this, { issueOperations ->
            issueOperations?.let {
                val feed = it.feedName
                val date = it.date
                val status = it.status
                getSectionListByIssue(feed, date, status).invokeOnCompletion {
                    lifecycleScope.launchWhenResumed {
                        if (displayableKey == null || displayableKey?.startsWith("sec") == true) {
                            showSection(displayableKey)
                        }
                    }
                }
                getArticles(feed, date, status).invokeOnCompletion {
                    lifecycleScope.launchWhenResumed {
                        if (displayableKey?.startsWith("art") == true) {
                            showArticle(displayableKey)
                        }
                        withContext(Dispatchers.IO) {
                            viewModel.sectionNameListLiveData.postValue(
                                viewModel.articleList.map { article ->
                                    article.getSectionStub(
                                        context?.applicationContext
                                    )?.key
                                }
                            )
                        }
                    }
                }
                getMainView()?.setCoverFlowItem(issueOperations)
                setDrawerIssue(issueOperations)
                getMainView()?.changeDrawerIssue()
            }
        }, { issueOperations ->
            issueOperations != null
        })
    }

    private fun showArticle(articleFileName: String? = null) {
        runIfNotNull(articleFileName, articlePagerFragment) { fileName, fragment ->
            showFragment(fragment)
            fragment.tryLoadArticle(fileName)
        }
    }

    private fun showSection(sectionFileName: String? = null) {
        showFragment(sectionPagerFragment)
        sectionFileName?.let {
            sectionPagerFragment.tryLoadSection(sectionFileName)
        }
    }

    private fun showFragment(fragment: Fragment) {
        val transaction = childFragmentManager.beginTransaction()
        childFragmentManager.fragments.forEach {
            transaction.hide(it)
        }
        transaction.show(fragment).commit()
    }

    private fun addFragment(fragment: Fragment) {
        val fragmentClass = fragment::class.java.toString()
        if (childFragmentManager.findFragmentByTag(fragmentClass) == null) {
            childFragmentManager.beginTransaction()
                .add(
                    R.id.fragment_issue_content_container, fragment, fragmentClass
                )
                .addToBackStack(fragmentClass)
                .hide(fragment)
                .commit()
        }
    }

    fun show(displayableName: String): Boolean {
        return if (displayableName.startsWith("art")) {
            viewModel.articleList.firstOrNull { it.key == displayableName }?.let {
                showArticle(displayableName)
                true
            } ?: false
        } else {
            viewModel.sectionList.firstOrNull { it.key == displayableName }
                ?.let {
                    showSection(displayableName)
                    true
                } ?: false
        }
    }

    private fun getIssueStub(issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        lifecycleScope.launch(Dispatchers.IO) {
            viewModel.issueOperationsLiveData.postValue(
                IssueRepository.getInstance(context?.applicationContext).getStub(
                    issueFeedName, issueDate, issueStatus
                )
            )
        }
    }

    private fun getIssueOperationByDisplayableKey() {
        displayableKey?.let {
            val issueRepository = IssueRepository.getInstance(context?.applicationContext)
            lifecycleScope.launch(Dispatchers.IO) {
                val issueStub = issueRepository.getIssueStubForSection(it)
                    ?: issueRepository.getIssueStubForArticle(it)
                viewModel.issueOperationsLiveData.postValue(issueStub)
            }
        }
    }

    private fun getArticles(issueFeedName: String, issueDate: String, issueStatus: IssueStatus) =
        lifecycleScope.launch(Dispatchers.IO) {
            val articles = ArticleRepository.getInstance(context?.applicationContext)
                .getArticleStubListForIssue(issueFeedName, issueDate, issueStatus)
            viewModel.articleList = articles
        }

    private fun getSectionListByIssue(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ) = lifecycleScope.launch(Dispatchers.IO) {
        val sections =
            SectionRepository.getInstance(context?.applicationContext).getSectionStubsForIssue(
                issueFeedName, issueDate, issueStatus
            )
        viewModel.sectionList = sections
    }

    override fun onBackPressed(): Boolean {
        return childFragmentManager.fragments.lastOrNull { it.isVisible }?.let {
            when ((it as? BackFragment?)?.onBackPressed()) {
                true -> true
                false -> {
                    (it as? ArticlePagerFragment)?.let {
                        showFragment(sectionPagerFragment)
                        true
                    } ?: false
                }
                null -> false
            }
        } ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val issueOperations = viewModel.issueOperationsLiveData.value
        outState.apply {
            putString(ISSUE_DATE, issueDate ?: issueOperations?.date)
            putString(ISSUE_FEED, issueFeedName ?: issueOperations?.feedName)
            putString(
                ISSUE_STATUS,
                issueStatus?.toString() ?: issueOperations?.status?.toString()
            )
            putString(DISPLAYABLE_KEY, displayableKey)
        }
    }
}
