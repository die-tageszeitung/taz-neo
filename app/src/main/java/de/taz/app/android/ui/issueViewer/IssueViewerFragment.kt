package de.taz.app.android.ui.issueViewer

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.*
import de.taz.app.android.base.BaseViewModelFragment
import de.taz.app.android.data.DataService
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.FragmentIssueContentBinding
import de.taz.app.android.monkey.*
import de.taz.app.android.persistence.repository.*
import de.taz.app.android.singletons.KeepScreenOnHelper
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.IssueLoaderFragment
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerViewModel
import de.taz.app.android.ui.webview.ImprintWebViewFragment
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.ui.webview.pager.SectionPagerFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.*

/**
 * Show an Issue with sections and articles in their respective pager fragments
 *
 * Additional fragments are loaded to ensure the transitions are smooth.
 *
 * [sectionPagerFragment] is used to show the sections of the issue
 * [articlePagerFragment] is used to show the articles of the issue
 * [imprintFragment] is used to show the imprint
 * [loaderFragment] shows the initial loading screen
 *
 */
class IssueViewerFragment : BaseViewModelFragment<IssueViewerViewModel, FragmentIssueContentBinding>(), BackFragment {

    override val viewModel: IssueViewerViewModel by activityViewModels()

    private val log by Log


    private lateinit var dataService: DataService
    private lateinit var sectionRepository: SectionRepository
    private lateinit var imageRepository: ImageRepository
    private lateinit var articleRepository: ArticleRepository
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var tazApiCssDataStore: TazApiCssDataStore

    private val sectionDrawerViewModel: SectionDrawerViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            addFragment(SectionPagerFragment())
            addFragment(ArticlePagerFragment())
            addFragment(ImprintWebViewFragment())
            addFragment(IssueLoaderFragment())
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dataService = DataService.getInstance(requireContext().applicationContext)
        sectionRepository = SectionRepository.getInstance(requireContext().applicationContext)
        imageRepository = ImageRepository.getInstance(requireContext().applicationContext)
        articleRepository = ArticleRepository.getInstance(requireContext().applicationContext)
        generalDataStore = GeneralDataStore.getInstance(requireContext().applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(requireContext().applicationContext)
    }

    override fun onResume() {
        super.onResume()
        viewModel.activeDisplayMode.observeDistinct(this.viewLifecycleOwner) {
            setDisplayMode(it)
        }


        // This block is to expand the drawer on the first time using this activity to alert the user of the side drawer
        lifecycleScope.launchWhenResumed {
            val timesDrawerShown = generalDataStore.drawerShownCount.get()
            if (sectionDrawerViewModel.drawerOpen.value == false && timesDrawerShown < DRAWER_SHOW_NUMBER) {
                delay(500)
                sectionDrawerViewModel.drawerOpen.value = true
                viewModel.issueKeyAndDisplayableKeyLiveData.observe(
                    this@IssueViewerFragment
                ) { issueWithDisplayable ->
                    if (issueWithDisplayable == null) return@observe
                    lifecycleScope.launch {
                        delay(1500)
                        sectionDrawerViewModel.drawerOpen.value = false
                        generalDataStore.drawerShownCount.set(timesDrawerShown + 1)
                    }

                }
            }
            tazApiCssDataStore.keepScreenOn.asFlow().collect {
                KeepScreenOnHelper.toggleScreenOn(it, activity)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.displayableKeyLiveData.observeDistinct(viewLifecycleOwner) {
            lifecycleScope.launch {
                val defaultDrawerFileName = resources.getString(R.string.DEFAULT_NAV_DRAWER_FILE_NAME)
                val navButton = when {
                    it == null -> imageRepository.get(defaultDrawerFileName)
                    it.startsWith("art") -> sectionRepository.getNavButtonForArticle(it)
                    it.startsWith("sec") -> sectionRepository.getNavButtonForSection(it)
                    else -> imageRepository.get(defaultDrawerFileName)
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
