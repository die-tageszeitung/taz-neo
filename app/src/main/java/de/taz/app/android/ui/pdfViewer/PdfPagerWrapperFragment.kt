package de.taz.app.android.ui.pdfViewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout.LayoutParams
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.ViewBindingFragment
import de.taz.app.android.coachMarks.LmdLogoCoachMark
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.ActivityPdfDrawerLayoutBinding
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.KeepScreenOnHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.SuccessfulLoginAction
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.drawer.DrawerViewController
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.showSdCardIssueDialog
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.showIssueDownloadFailedDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException

class PdfPagerWrapperFragment: ViewBindingFragment<ActivityPdfDrawerLayoutBinding>(), SuccessfulLoginAction, BackFragment {

    companion object {
        private const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"
        private const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE"

        const val ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME =
            "ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME"

        fun newInstance(
            issuePublication: IssuePublicationWithPages,
            displayableKey: String? = null
        ) = PdfPagerWrapperFragment().apply {
            arguments = bundleOf(
                KEY_ISSUE_PUBLICATION to issuePublication,
                KEY_DISPLAYABLE to displayableKey,
            )
        }
    }

    private val pdfPagerViewModel by viewModels<PdfPagerViewModel>()
    private val issueContentViewModel by activityViewModels<IssueViewerViewModel>()
    private val drawerAndLogoViewModel by activityViewModels<DrawerAndLogoViewModel>()

    private val log by Log

    private lateinit var issuePublication: IssuePublicationWithPages

    private lateinit var authHelper: AuthHelper
    private lateinit var storageService: StorageService
    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private lateinit var toastHelper: ToastHelper
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var drawerViewController: DrawerViewController

    // mutable instance state
    private var navButton: Image? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)

        authHelper = AuthHelper.getInstance(context.applicationContext)
        storageService = StorageService.getInstance(context.applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(context.applicationContext)
        toastHelper = ToastHelper.getInstance(context.applicationContext)
        generalDataStore = GeneralDataStore.getInstance(context.applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        issuePublication = try {
            arguments?.getParcelable(KEY_ISSUE_PUBLICATION)
                ?: throw IllegalStateException("PdfPagerActivity needs to be started with KEY_ISSUE_KEY in Intent extras of type IssueKey")
        } catch (e: ClassCastException) {
            log.warn("Somehow we got IssuePublication instead of IssuePublicationWithPages, so we wrap it it", e)
            SentryWrapper.captureException(e)
            IssuePublicationWithPages(
                arguments?.getParcelable(KEY_ISSUE_PUBLICATION)!!
            )
        }

        pdfPagerViewModel.setIssuePublication(issuePublication)

        pdfPagerViewModel.issueDownloadFailedErrorFlow
            .filter { it }
            .asLiveData()
            .observe(this) {
                requireActivity().showIssueDownloadFailedDialog(issuePublication)
            }

        pdfPagerViewModel.navButton.observe(this) {
            if (it != null) {
                lifecycleScope.launch { showNavButton(it) }
            }
        }

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction().add(
                R.id.activity_pdf_fragment_placeholder,
                PdfPagerFragment()
            ).commit()
        }

        val displayableKey: String? = arguments?.getString(KEY_DISPLAYABLE)
        if (displayableKey != null) {
            arguments?.remove(KEY_DISPLAYABLE)
            val issueObserver = object : Observer<IssueStub?> {
                override fun onChanged(issueStub: IssueStub?) {
                    if (issueStub != null) {
                        lifecycleScope.launch {
                            showArticle(issueStub.issueKey, displayableKey)
                        }
                        pdfPagerViewModel.issueStubLiveData.removeObserver(this)
                    }
                }
            }
            pdfPagerViewModel.issueStubLiveData.observe(this, issueObserver)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    pdfPagerViewModel.showSubscriptionElapsedFlow
                        .distinctUntilChanged()
                        .filter { it }
                        .collect {
                            SubscriptionElapsedBottomSheetFragment.showSingleInstance(childFragmentManager)
                        }
                }

                launch {
                    pdfPagerViewModel.openLinkEventFlow.filterNotNull()
                        .collect {
                            when (it) {
                                is OpenLinkEvent.OpenExternal ->
                                    openExternally(it.link)

                                is OpenLinkEvent.ShowImprint ->
                                    showImprint(it.issueKeyWithDisplayableKey)

                                is OpenLinkEvent.ShowArticle ->
                                    showArticle(it.issueKey, it.displayableKey)
                            }
                            pdfPagerViewModel.linkEventIsConsumed()
                        }
                }

                launch {
                    tazApiCssDataStore.keepScreenOn.asFlow().collect {
                        KeepScreenOnHelper.toggleScreenOn(it, requireActivity())
                    }
                }

                launch {
                    drawerAndLogoViewModel.drawerState.collect {
                        drawerViewController.handleDrawerLogoState(it)
                    }
                }
            }
        }

        childFragmentManager.addOnBackStackChangedListener {
            // When the articlePagerFragment is popped from the backstack via the back functionality
            // the logo shall be hidden again
            if (childFragmentManager.backStackEntryCount == 0) {
                drawerAndLogoViewModel.setFeedLogoAndHide()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewBinding.apply {
            drawerViewController = DrawerViewController(
                requireContext(),
                pdfDrawerLayout,
                drawerLogoWrapper,
                navView,
                view
            )

            // Adjust extra padding when we have cutout display
            lifecycleScope.launch {
                val extraPadding = generalDataStore.displayCutoutExtraPadding.get()
                if (extraPadding > 0 && resources.configuration.orientation == ORIENTATION_PORTRAIT) {
                    navView.setPadding(0, extraPadding, 0 ,0)
                }
            }

            pdfDrawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    drawerViewController.handleOnDrawerSlider(slideOffset)
                }

                override fun onDrawerOpened(drawerView: View) {
                    lifecycleScope.launch {
                        LmdLogoCoachMark.setFunctionAlreadyDiscovered(requireContext().applicationContext)
                    }
                    drawerAndLogoViewModel.openDrawer()
                }

                override fun onDrawerClosed(drawerView: View) {
                    drawerAndLogoViewModel.closeDrawer()
                }

                override fun onDrawerStateChanged(newState: Int) = Unit
            })

            drawerLogo.setOnClickListener {
                drawerAndLogoViewModel.closeDrawer()
            }
        }
    }

    override fun onLogInSuccessful(articleName: String?) {
        // Launch the Activity restarting logic from the application scope to prevent it from being
        // accidentally canceled due the the activity being finished
        viewLifecycleOwner.lifecycleScope.launch {
            // Restart the activity if this is *not* a Week/Wochentaz abo
            if (!authHelper.isLoginWeek.get()) {
                articleName
                    ?.takeIf { authHelper.isValid() }
                    ?.replace("public.", "")
                    ?.let { articleNameRegular ->
                        MainActivity.start(requireContext(), issuePublication, articleNameRegular)
                    }
            } else {
                toastHelper.showToast(R.string.toast_login_week, long = true)
            }
        }
    }

    private suspend fun showNavButton(navButton: Image) {
        val navButtonPath = storageService.getAbsolutePath(navButton)
        if (this.navButton != navButton && navButtonPath != null) {
            try {
                val imageDrawable = withContext(Dispatchers.IO) {
                    Glide
                        .with(this@PdfPagerWrapperFragment)
                        .load(navButtonPath)
                        .submit()
                        .get()
                }

                // scale factor determined in resources
                val scaleFactor = resources.getFraction(
                    R.fraction.nav_button_scale_factor,
                    1,
                    33
                )
                val logicalWidth = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    imageDrawable.intrinsicWidth.toFloat(),
                    resources.displayMetrics
                ) * scaleFactor

                drawerViewController.drawerLogoWidth = logicalWidth.toInt()

                val logicalHeight = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    imageDrawable.intrinsicHeight.toFloat(),
                    resources.displayMetrics
                ) * scaleFactor

                withContext(Dispatchers.Main) {
                    viewBinding.drawerLogo.apply {
                        setImageDrawable(imageDrawable)
                        updateLayoutParams<LayoutParams> {
                            width = logicalWidth.toInt()
                            height = logicalHeight.toInt()
                        }
                    }
                }
                this.navButton = navButton

                LmdLogoCoachMark(this, viewBinding.drawerLogo, imageDrawable)
                    .maybeShow()

            } catch (e: ExecutionException) {
                val hint = "Glide could not get imageDrawable. Probably a SD-Card issue."
                log.error(hint, e)
                SentryWrapper.captureException(e)
                showSdCardIssueDialog()
            }
        }
    }

    /**
     * May be used from child fragments to show an Article within the [ArticlePagerFragment]
     */
    fun showArticle(article: ArticleOperations) {
        lifecycleScope.launch {
            pdfPagerViewModel.issueStub?.let { issueStub ->
                val displayableKey = article.key
                showArticle(issueStub.issueKey, displayableKey)
            }
        }
    }

    private suspend fun showArticle(issueKey: IssueKey, displayableKey: String?) {
        issueContentViewModel.setDisplayable(issueKey, displayableKey)
        showArticlePagerFragment(ArticlePagerFragment())
    }

    private fun showImprint(issueKeyWithDisplayableKey: IssueKeyWithDisplayableKey) {
        issueContentViewModel.setDisplayable(issueKeyWithDisplayableKey)
        showArticlePagerFragment(ArticlePagerFragment())
    }

    private fun showArticlePagerFragment(fragment: Fragment) {
        if (childFragmentManager.findFragmentByTag(ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE) == null) {
            childFragmentManager.commit {
                add(
                    R.id.activity_pdf_fragment_placeholder,
                    fragment,
                    ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
                )
                addToBackStack(ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME)
            }
        }
    }

    private fun openExternally(url: String) {
        val context = requireContext()
        val color = ContextCompat.getColor(context, R.color.colorAccent)
        try {
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder().setToolbarColor(color).build()
                )
                .build()
                .apply { launchUrl(context, Uri.parse(url)) }
        } catch (e: ActivityNotFoundException) {
            val toastHelper = ToastHelper.getInstance(context.applicationContext)
            if (url.startsWith("mailto:")) {
                toastHelper.showToast(R.string.toast_no_email_client)
            } else {
                toastHelper.showToast(R.string.toast_unknown_error)
            }
        }
    }

    private fun popArticlePagerFragmentIfOpen(): Boolean {
        return childFragmentManager.popBackStackImmediate(
            ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
    }

    override fun onBackPressed(): Boolean {
        return popArticlePagerFragmentIfOpen()
    }
}