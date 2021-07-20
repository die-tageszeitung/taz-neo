package de.taz.app.android.ui.issueViewer

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.*
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.data.DataService
import de.taz.app.android.monkey.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.IssueLoaderFragment
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerViewModel
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
    private lateinit var preferences: SharedPreferences
    private lateinit var sectionRepository: SectionRepository
    private lateinit var imageRepository: ImageRepository
    private lateinit var articleRepository: ArticleRepository

    private val sectionDrawerViewModel: SectionDrawerViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = requireActivity().getSharedPreferences(
            PREFERENCES_GENERAL,
            AppCompatActivity.MODE_PRIVATE
        )
        sectionPagerFragment = SectionPagerFragment()
        articlePagerFragment = ArticlePagerFragment()
        imprintFragment = ImprintWebViewFragment()
        loaderFragment = IssueLoaderFragment()
        addFragment(sectionPagerFragment)
        addFragment(articlePagerFragment)
        addFragment(imprintFragment)
        addFragment(loaderFragment)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dataService = DataService.getInstance(requireContext().applicationContext)
        sectionRepository = SectionRepository.getInstance(requireContext().applicationContext)
        imageRepository = ImageRepository.getInstance(requireContext().applicationContext)
        articleRepository = ArticleRepository.getInstance(requireContext().applicationContext)
    }

    override fun onResume() {
        super.onResume()
        viewModel.activeDisplayMode.observeDistinct(this.viewLifecycleOwner) {
            setDisplayMode(it)
        }


        // This block is to expand the drawer on the first time using this activity to alert the user of the side drawer
        lifecycleScope.launchWhenResumed {
            val timesDrawerShown = preferences.getInt(PREFERENCES_GENERAL_DRAWER_SHOWN_NUMBER, 0)
            if (sectionDrawerViewModel.drawerOpen.value == false && timesDrawerShown < DRAWER_SHOW_NUMBER) {
                delay(500)
                sectionDrawerViewModel.drawerOpen.value = true
                viewModel.issueKeyAndDisplayableKeyLiveData.observe(
                    this@IssueViewerFragment
                ) { issueWithDisplayable ->
                    if (issueWithDisplayable == null) return@observe
                    CoroutineScope(Dispatchers.IO).launch {
                        val issue = dataService.getIssue(
                            IssuePublication(issueWithDisplayable.issueKey),
                            retryOnFailure = true
                        )
                        dataService.ensureDownloaded(issue)
                        delay(1500)
                        withContext(Dispatchers.Main) {
                            sectionDrawerViewModel.drawerOpen.value = false
                        }
                        preferences.edit().apply {
                            putInt(
                                PREFERENCES_GENERAL_DRAWER_SHOWN_NUMBER,
                                timesDrawerShown + 1
                            )
                            apply()
                        }
                    }

                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.displayableKeyLiveData.observeDistinct(viewLifecycleOwner) {
            lifecycleScope.launch(Dispatchers.IO) {
                val navButton = when {
                    it == null -> imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME)
                    it.startsWith("art") -> sectionRepository.getNavButtonForArticle(it)
                    it.startsWith("sec") -> sectionRepository.getNavButtonForSection(it)
                    else -> imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME)
                }
                sectionDrawerViewModel.navButton.postValue(navButton)
            }
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
                    val lastSectionKey = viewModel.lastSectionKey
                        ?: viewModel.currentDisplayable?.let { displayableKey ->
                            if (displayableKey.startsWith("art")) {
                                runBlocking(Dispatchers.IO) {
                                    sectionRepository.getSectionStubForArticle(
                                        displayableKey
                                    )?.key
                                }
                            } else {
                                null
                            }
                        }
                    runIfNotNull(
                        viewModel.issueKeyAndDisplayableKeyLiveData.value?.issueKey,
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
