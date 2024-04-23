package de.taz.app.android.ui.webview

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.databinding.FragmentWebviewSectionBinding
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import kotlinx.coroutines.launch


class SectionImprintWebViewFragment : WebViewFragment<
        Article,
        WebViewViewModel<Article>,
        FragmentWebviewSectionBinding
        >() {

    private lateinit var generalDataStore: GeneralDataStore

    override val viewModel by lazy {
        ViewModelProvider(
            this, SavedStateViewModelFactory(
                this.requireActivity().application, this
            )
        )[ArticleWebViewViewModel::class.java]
    }

    private val issueContentViewModel: IssueViewerViewModel by activityViewModels()

    override val webView: AppWebView
        get() = viewBinding.webView

    override val nestedScrollView: NestedScrollView
        get() = viewBinding.nestedScrollView

    override val loadingScreen: View
        get() = viewBinding.loadingScreen.root

    override val appBarLayout: AppBarLayout
        get() = viewBinding.appBarLayout

    override val navigationBottomLayout: ViewGroup? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        issueContentViewModel.imprintArticleLiveData.observe(this) {
            if (it != null) {
                viewModel.displayableLiveData.postValue(it)
            }
        }
    }

    override fun onPageRendered() {
        super.onPageRendered()
        hideLoadingScreen()
    }

    override fun setHeader(displayable: Article) {
        viewLifecycleOwner.lifecycleScope.launch {
            // Keep a copy of the current context while running this coroutine.
            // This is necessary to prevent from a crash while calling requireContext() if the
            // Fragment was already being destroyed.
            // If there is no more context available we return from the coroutine immediately.
            val context = context ?: return@launch

            val issueStub = displayable.getIssueStub(context.applicationContext) ?: return@launch
            val isWeekend = issueStub.isWeekend && issueStub.validityDate.isNullOrBlank()
            val isWochentaz = issueStub.isWeekend && !issueStub.validityDate.isNullOrBlank()

            viewBinding.apply {
                section.apply {
                    isVisible = true
                    setText(R.string.imprint)

                    if (isWeekend || isWochentaz) {
                        typeface = ResourcesCompat.getFont(context, R.font.appFontKnileSemiBold)
                    }
                }

                issueDate.isVisible = true
                weekendIssueDate.isVisible = false
            }
        }
        applyExtraPaddingOnCutoutDisplay()
    }

    override fun reloadAfterCssChange() {
        viewLifecycleOwner.lifecycleScope.launch {
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

    /**
     * Adjust padding when we have cutout display
     */
    private fun applyExtraPaddingOnCutoutDisplay() {
        viewLifecycleOwner.lifecycleScope.launch {
            val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
            if (extraPadding > 0 && resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                viewBinding.collapsingToolbarLayout.setPadding(0, extraPadding, 0, 0)
            }
        }
    }
}