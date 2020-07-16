package de.taz.app.android.ui.webview.pager

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IssueContentFragment :
    BaseViewModelFragment<IssueContentViewModel>(R.layout.fragment_issue_content), BackFragment {

    override val enableSideBar: Boolean = true

    private var displayableKey: String? = null
    private var issueFeedName: String? = null
    private var issueDate: String? = null
    private var issueStatus: IssueStatus? = null

    private var sectionPagerFragment: SectionPagerFragment? = null
    private var articlePagerFragment: ArticlePagerFragment? = null


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

        displayableKey?.let {
            getIssueOperationByDisplayableKey()
        }

        runIfNotNull(issueFeedName, issueDate, issueStatus) { feed, date, status ->
            viewModel.issueFeedNameLiveData.postValue(feed)
            viewModel.issueDateLiveData.postValue(date)
            viewModel.issueStatusLiveData.postValue(status)
            getSectionListByIssue(feed, date, status).invokeOnCompletion { showSection(displayableKey) }
            getIssueStub(feed, date, status)
            getArticles(feed, date, status)
        }

    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        sectionPagerFragment =
            sectionPagerFragment ?: SectionPagerFragment.createInstance(viewModel)
        articlePagerFragment =
            articlePagerFragment ?: ArticlePagerFragment.createInstance(viewModel)
    }

    private fun showArticle(articleFileName: String) {
        articlePagerFragment?.let {
            showFragment(it)
            it.tryLoadArticle(articleFileName)
        }
    }

    private fun showSection(sectionFileName: String? = null) {
        sectionPagerFragment?.let { sectionPagerFragment ->
            showFragment(sectionPagerFragment)
            sectionFileName?.let {
                sectionPagerFragment.tryLoadSection(sectionFileName)
            }
        }
    }

    fun showFragment(fragment: Fragment) {
        val fragmentclass = fragment::class.java.toString()
        childFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_issue_content_container, fragment, fragmentclass
            )
            .addToBackStack(fragmentclass)
            .commit()

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

    private fun getArticles(issueFeedName: String, issueDate: String, issueStatus: IssueStatus) {
        lifecycleScope.launch(Dispatchers.IO) {
            val articles = ArticleRepository.getInstance(context?.applicationContext)
                .getArticleStubListForIssue(issueFeedName, issueDate, issueStatus)
            viewModel.articleList = articles
        }
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
        return childFragmentManager.fragments.lastOrNull()?.let {
            (it as? BackFragment)?.onBackPressed() ?: false
        } ?: false
    }
}
