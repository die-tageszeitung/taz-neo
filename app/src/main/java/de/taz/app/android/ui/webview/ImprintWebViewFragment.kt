package de.taz.app.android.ui.webview

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.databinding.FragmentWebviewImprintBinding
import de.taz.app.android.persistence.repository.FileEntryRepository
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setupBottomNavigation
import kotlinx.coroutines.launch

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
        storageService = StorageService.getInstance(context.applicationContext)
        fileEntryRepository = FileEntryRepository.getInstance(context.applicationContext)
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
            // Keep a copy of the current context while running this coroutine.
            // This is necessary to prevent from a crash while calling requireContext() if the
            // Fragment was already being destroyed.
            // If there is no more context available we return from the coroutine immediately.
            val context = context ?: return@launch

            val sectionView = view?.findViewById<TextView>(R.id.section)
            sectionView?.setText(R.string.imprint)

            val issueStub = displayable.getIssueStub(context.applicationContext)
            if (issueStub?.isWeekend == true) {
                sectionView?.typeface = ResourcesCompat.getFont(context, R.font.appFontKnileSemiBold)
            }
        }
    }

    override fun reloadAfterCssChange() {
        lifecycleScope.launch {
            whenCreated {
                if (!isRendered) {
                    return@whenCreated
                }

                webView.injectCss()
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N)
                    webView.reload()
            }
        }
    }
}