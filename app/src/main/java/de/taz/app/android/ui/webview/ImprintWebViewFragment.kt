package de.taz.app.android.ui.webview

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.Article
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerViewModel
import de.taz.app.android.ui.issueViewer.IssueContentDisplayMode
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImprintWebViewFragment :
    WebViewFragment<Article, WebViewViewModel<Article>>(R.layout.fragment_webview_imprint) {

    override val nestedScrollViewId = R.id.nested_scroll_view

    override val bottomNavigationMenuRes = R.menu.navigation_bottom_section
    override val viewModel by lazy {
        ViewModelProvider(
            this, SavedStateViewModelFactory(
                this.requireActivity().application, this
            )
        ).get(ArticleWebViewViewModel::class.java)
    }

    private val issueContentViewModel: IssueViewerViewModel by lazy {
        ViewModelProvider(
            requireActivity().viewModelStore, SavedStateViewModelFactory(
                requireActivity().application, requireActivity()
            )
        ).get(IssueViewerViewModel::class.java)
    }

    private val drawerViewModel: SectionDrawerViewModel by activityViewModels()

    private lateinit var articleRepository: ArticleRepository
    private lateinit var issueRepository: IssueRepository
    private lateinit var storageService: StorageService
    private lateinit var fileEntryRepository: FileEntryRepository

    override fun onAttach(context: Context) {
        super.onAttach(context)

        articleRepository = ArticleRepository.getInstance(requireContext().applicationContext)
        issueRepository = IssueRepository.getInstance(requireContext().applicationContext)
        storageService = StorageService.getInstance(requireContext().applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(requireContext().applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        issueContentViewModel.displayableKeyLiveData.observe(this) {
            log.debug("I received displayable $it")
            if (it != null) {
                if (it.startsWith("art") && it == issueContentViewModel.imprintArticleLiveData.value?.key) {
                    issueContentViewModel.activeDisplayMode.postValue(IssueContentDisplayMode.Imprint)
                }
            }
        }

        issueContentViewModel.imprintArticleLiveData.observe(this) {
            if (it != null) {
                viewModel.displayableLiveData.postValue(it)
            }
            if (it?.key == issueContentViewModel.displayableKeyLiveData.value) {
                issueContentViewModel.activeDisplayMode.postValue(IssueContentDisplayMode.Imprint)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        drawerViewModel.setDefaultDrawerNavButton()
    }

    override fun setHeader(displayable: Article) {
        lifecycleScope.launch(Dispatchers.IO) {
            val title = getString(R.string.imprint)
            activity?.runOnUiThread {
                view?.findViewById<TextView>(R.id.section)?.apply {
                    text = title
                    setOnClickListener {
                        requireActivity().finish()
                    }
                }
            }

            val issueStub = displayable.getIssueStub()
            issueStub?.apply {
                if (isWeekend) {
                    val weekendTypefaceFileEntry =
                        fileEntryRepository.get(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)
                    val weekendTypefaceFile = weekendTypefaceFileEntry?.let(storageService::getFile)
                    weekendTypefaceFile?.let {
                        FontHelper.getInstance(context?.applicationContext)
                            .getTypeFace(it)?.let { typeface ->
                                withContext(Dispatchers.Main) {
                                    view?.findViewById<TextView>(R.id.section)?.typeface = typeface
                                    view?.findViewById<TextView>(R.id.article_num)?.typeface =
                                        typeface
                                }
                            }
                    }
                }
            }
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                requireActivity().finish()
            }
            R.id.bottom_navigation_action_size -> {
                showBottomSheet(TextSettingsFragment())
            }
        }
    }
}