package de.taz.app.android.ui.webview.pager

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.DRAWER_SHOW_NUMBER
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.PREFERENCES_GENERAL_DRAWER_SHOWN_NUMBER
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.*
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.download.DownloadService
import de.taz.app.android.monkey.observeDistinct
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.ToDownloadIssueHelper
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.webview.ImprintWebViewFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.SharedPreferenceIntLiveData
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.*

const val ISSUE_DATE = "issueDate"
const val ISSUE_FEED = "issueFeed"
const val ISSUE_STATUS = "issueStatus"

class IssueContentFragment :
    BaseViewModelFragment<IssueContentViewModel>(R.layout.fragment_issue_content), BackFragment {

    override val viewModel: IssueContentViewModel by lazy {
        ViewModelProvider(
            requireActivity(),
            SavedStateViewModelFactory(this.requireActivity().application, requireActivity())
        ).get(IssueContentViewModel::class.java)
    }

    private val log by Log

    override val enableSideBar: Boolean = true

    private lateinit var sectionPagerFragment: SectionPagerFragment
    private lateinit var articlePagerFragment: ArticlePagerFragment
    private lateinit var imprintFragment: ImprintWebViewFragment

    private var drawerShown: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sectionPagerFragment = SectionPagerFragment()
        articlePagerFragment = ArticlePagerFragment()
        imprintFragment = ImprintWebViewFragment()
        addFragment(sectionPagerFragment)
        addFragment(articlePagerFragment)
        addFragment(imprintFragment)

        viewModel.issueStubAndDisplayableKeyLiveData.observeDistinct(
            this,
            { (issueStub, _) ->
                lifecycleScope.launchWhenResumed {
                    val drawerShownLiveData = getShownDrawerNumberLiveData()
                    if (!drawerShown && drawerShownLiveData.value < DRAWER_SHOW_NUMBER) {
                        drawerShown = true
                        delay(100)
                        getMainView()?.openDrawer(GravityCompat.START)
                        drawerShownLiveData.postValue(drawerShownLiveData.value + 1)
                    }

                    if (issueStub.dateDownload == null) {
                        withContext(Dispatchers.IO) {
                            IssueRepository.getInstance(context?.applicationContext)
                                .getIssue(issueStub).let { issue ->
                                    DownloadService.getInstance(context?.applicationContext)
                                        .download(issue)
                                }
                        }
                    }
                }
            })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeAuthStatusAndChangeIssue()
        viewModel.activeDisplayMode.observe(this.viewLifecycleOwner) {
            setDisplayMode(it)
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
            IssueContentDisplayMode.Imprint -> ImprintWebViewFragment::class.java
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
                    (it as? ArticlePagerFragment)?.let {
                        setDisplayMode(IssueContentDisplayMode.Section)
                        true
                    } ?: false
                }
                null -> false
            }
        } ?: false
    }

    private fun observeAuthStatusAndChangeIssue() {
        val authHelper = AuthHelper.getInstance(context?.applicationContext)
        val apiService = ApiService.getInstance(context?.applicationContext)
        val issueRepository = IssueRepository.getInstance(context?.applicationContext)

        authHelper.authStatusLiveData.observe(viewLifecycleOwner) { authStatus ->
            val issueStub = viewModel.issueStubAndDisplayableKeyLiveData.value?.first
            if (authStatus == AuthStatus.valid && issueStub?.status == IssueStatus.public) {
                runIfNotNull(issueStub.feedName, issueStub.date) { feedName, date ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        apiService.getIssueByFeedAndDate(feedName, date)?.let {
                            issueRepository.saveIfDoesNotExist(it)
                            viewModel.setDisplayable(
                                it.issueKey,
                                viewModel.issueStubAndDisplayableKeyLiveData.value?.second!!.replace(
                                    "public.",
                                    ""
                                )
                            )
                            ToDownloadIssueHelper.getInstance().startMissingDownloads(
                                it.date
                            )
                        }
                    }
                }
            }
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
