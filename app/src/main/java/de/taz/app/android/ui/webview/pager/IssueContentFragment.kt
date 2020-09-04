package de.taz.app.android.ui.webview.pager

import android.content.Context
import android.os.Bundle
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.DRAWER_SHOW_NUMBER
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.PREFERENCES_GENERAL_DRAWER_SHOWN_NUMBER
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.AuthStatus
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.download.DownloadService
import de.taz.app.android.monkey.observeDistinctUntil
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.webview.ImprintFragment
import de.taz.app.android.util.SharedPreferenceIntLiveData
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.*

const val ISSUE_DATE = "issueDate"
const val ISSUE_FEED = "issueFeed"
const val ISSUE_STATUS = "issueStatus"
const val DISPLAYABLE_KEY = "webViewDisplayableKey"
const val SHOW_SECTIONS = "showSection"

const val DRAWER_SHOWN = "drawerShown"

class IssueContentFragment :
    BaseViewModelFragment<IssueContentViewModel>(R.layout.fragment_issue_content), BackFragment {

    override val enableSideBar: Boolean = true

    private var displayableKey: String? = null
    private var issueFeedName: String? = null
    private var issueDate: String? = null
    private var issueStatus: IssueStatus? = null

    private val sectionPagerFragment: SectionPagerFragment
        get() = childFragmentManager.findFragmentByTag(
            SectionPagerFragment::class.java.toString()
        ) as SectionPagerFragment

    private val articlePagerFragment: ArticlePagerFragment
        get() = childFragmentManager.findFragmentByTag(
            ArticlePagerFragment::class.java.toString()
        ) as ArticlePagerFragment

    private val imprintFragment: ImprintFragment
        get() = childFragmentManager.findFragmentByTag(
            ImprintFragment::class.java.toString()
        ) as ImprintFragment

    private var showSections: Boolean = true
    private var drawerShown: Boolean = false

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

        addFragment(SectionPagerFragment())
        addFragment(ArticlePagerFragment())

        savedInstanceState?.apply {
            issueDate = getString(ISSUE_DATE)
            issueFeedName = getString(ISSUE_FEED)
            try {
                issueStatus = getString(ISSUE_STATUS)?.let { IssueStatus.valueOf(it) }
            } catch (e: IllegalArgumentException) {
                // do nothing issueStatus is null
            }
            displayableKey = getString(DISPLAYABLE_KEY)
            showSections = getBoolean(SHOW_SECTIONS, false)
            drawerShown = getBoolean(DRAWER_SHOWN, false)
        }


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
                        if ((displayableKey == null && showSections)
                            || displayableKey?.startsWith("sec") == true
                        ) {
                            showSection(displayableKey)
                        }
                    }
                }
                getArticles(feed, date, status).invokeOnCompletion {
                    lifecycleScope.launchWhenResumed {
                        if ((displayableKey == null && !showSections)
                            || (displayableKey?.startsWith("art") == true
                                    && displayableKey?.equals(viewModel.imprint?.articleFileName) == false)
                        ) {
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
                getImprint(feed, date, status).invokeOnCompletion {
                    addFragment(ImprintFragment()) {
                        if (displayableKey?.equals(viewModel.imprint?.articleFileName) == true) {
                            showImprint()
                        }
                    }
                }
                lifecycleScope.launchWhenResumed {
                    setDrawerIssue(issueOperations)
                    getMainView()?.changeDrawerIssue()
                    val drawerShownLiveData = getShownDrawerNumberLiveData()
                    if (!drawerShown && drawerShownLiveData.value < DRAWER_SHOW_NUMBER) {
                        drawerShown = true
                        delay(100)
                        getMainView()?.openDrawer(GravityCompat.START)
                        drawerShownLiveData.postValue(drawerShownLiveData.value + 1)
                    }
                }
                if (issueOperations.dateDownload == null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        IssueRepository.getInstance(context?.applicationContext)
                            .getIssue(issueOperations)?.let { issue ->
                                DownloadService.getInstance(context?.applicationContext)
                                    .download(issue)
                            }
                    }
                }
            }
        }, { issueOperations ->
            issueOperations != null
        })

        lifecycleScope.launchWhenResumed {
            goToRegularIssueAfterLogin()
        }

    }

    private fun showArticle(articleFileName: String? = null) {
        showSections = false
        runIfNotNull(articleFileName, articlePagerFragment) { fileName, fragment ->
            showFragment(fragment)
            fragment.tryLoadArticle(fileName)
        }
    }

    private fun showImprint() {
        showSections = false
        getMainView()?.setActiveDrawerSection(RecyclerView.NO_POSITION)
        showFragment(imprintFragment)
    }

    private fun showSection(sectionFileName: String? = null) {
        showSections = true
        showFragment(sectionPagerFragment)
        sectionPagerFragment.tryLoadSection(sectionFileName)
    }

    private fun showFragment(fragment: Fragment) {
        val transaction = childFragmentManager.beginTransaction()
        childFragmentManager.fragments.forEach {
            transaction.hide(it)
        }
        val fragmentClass = fragment::class.java.toString()
        childFragmentManager.findFragmentByTag(fragmentClass)?.let {
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

    fun show(displayableName: String): Boolean {
        return if (displayableName.startsWith("art")) {
            viewModel.articleList.firstOrNull { it.key == displayableName }?.let {
                showArticle(displayableName)
                true
            } ?: viewModel.imprint?.let {
                showImprint()
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

    private fun getIssueStub(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ) {
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
                    ?: issueRepository.getIssueStubByImprintFileName(it)
                viewModel.issueOperationsLiveData.postValue(issueStub)
            }
        }
    }

    private fun getArticles(
        issueFeedName: String,
        issueDate: String,
        issueStatus: IssueStatus
    ) =
        lifecycleScope.launch(Dispatchers.IO) {
            val articles = ArticleRepository.getInstance(context?.applicationContext)
                .getArticleStubListForIssue(issueFeedName, issueDate, issueStatus)
            viewModel.articleList = articles
        }

    private fun getImprint(issueFeedName: String, issueDate: String, issueStatus: IssueStatus) =
        lifecycleScope.launch(Dispatchers.IO) {
            val imprint = IssueRepository.getInstance(context?.applicationContext)
                .getImprintStub(issueFeedName, issueDate, issueStatus)
            viewModel.imprint = imprint
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
            putString(DISPLAYABLE_KEY, displayableKey)
            putString(ISSUE_DATE, issueDate ?: issueOperations?.date)
            putString(ISSUE_FEED, issueFeedName ?: issueOperations?.feedName)
            putString(
                ISSUE_STATUS,
                issueStatus?.toString() ?: issueOperations?.status?.toString()
            )
            putBoolean(SHOW_SECTIONS, showSections)
            putBoolean(DRAWER_SHOWN, drawerShown)
        }
    }

    private fun goToRegularIssueAfterLogin() {
        if (issueStatus == IssueStatus.public) {
            val authHelper = AuthHelper.getInstance(context?.applicationContext)
            val apiService = ApiService.getInstance(context?.applicationContext)
            val issueRepository = IssueRepository.getInstance(context?.applicationContext)

            authHelper.authStatusLiveData.observe(viewLifecycleOwner) { authStatus ->
                if (authStatus == AuthStatus.valid) {
                    runIfNotNull(issueFeedName, issueDate) { feedName, date ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            apiService.getIssueByFeedAndDate(feedName, date)?.let {
                                issueRepository.saveIfDoesNotExist(it)
                                getMainView()?.switchToDisplayableAfterLogin(
                                    getCurrentlyShownDisplayableKey()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getCurrentlyShownDisplayableKey(): String {
        return if (showSections) {
            viewModel.sectionList[
                    sectionPagerFragment.viewModel.currentPosition
            ].sectionFileName.replace("public.", "")
        } else {
            viewModel.articleList[
                    articlePagerFragment.viewModel.currentPosition
            ].articleFileName.replace("public.", "")
        }
    }


    private fun getShownDrawerNumberLiveData(): SharedPreferenceIntLiveData {
        return SharedPreferenceIntLiveData(
            requireActivity().getSharedPreferences(PREFERENCES_GENERAL, Context.MODE_PRIVATE),
            PREFERENCES_GENERAL_DRAWER_SHOWN_NUMBER,
            0
        )
    }

}
