package de.taz.app.android.ui.webview

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.KNILE_SEMIBOLD_RESOURCE_FILE_NAME
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.databinding.FragmentWebviewImprintBinding
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.issueViewer.IssueContentDisplayMode
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImprintWebViewFragment : WebViewFragment<
        Article,
        WebViewViewModel<Article>,
        FragmentWebviewImprintBinding
        >() {

    override val nestedScrollViewId = R.id.web_view_wrapper

    override val viewModel by lazy {
        ViewModelProvider(
            this, SavedStateViewModelFactory(
                this.requireActivity().application, this
            )
        )[ArticleWebViewViewModel::class.java]
    }

    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()
    private lateinit var storageService: StorageService
    private lateinit var fileEntryRepository: FileEntryRepository

    override fun onAttach(context: Context) {
        super.onAttach(context)
        storageService = StorageService.getInstance(requireContext().applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(requireContext().applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        issueContentViewModel.imprintArticleLiveData.observe(this) {
            if (it != null) {
                viewModel.displayableLiveData.postValue(it)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireActivity().setupBottomNavigation(
            viewBinding.navigationBottomImprint,
            BottomNavigationItem.ChildOf(BottomNavigationItem.Home)
        )
    }

    override fun setHeader(displayable: Article) {
        viewLifecycleOwner.lifecycleScope.launch {
            val title = getString(R.string.imprint)
            view?.findViewById<TextView>(R.id.section)?.apply {
                text = title
            }

            // Keep a copy of the current context while running this coroutine.
            // This is necessary to prevent from a crash while calling requireContext() if the
            // Fragment was already being destroyed.
            // If there is no more context available we return from the coroutine immediately.
            val context = this@ImprintWebViewFragment.context ?: return@launch

            val issueStub = displayable.getIssueStub(context.applicationContext)
            issueStub?.apply {
                if (isWeekend) {
                    val weekendTypefaceFileEntry =
                        fileEntryRepository.get(KNILE_SEMIBOLD_RESOURCE_FILE_NAME)
                    val weekendTypefaceFile = weekendTypefaceFileEntry?.let(storageService::getFile)
                    weekendTypefaceFile?.let {
                        FontHelper.getInstance(context.applicationContext)
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
}