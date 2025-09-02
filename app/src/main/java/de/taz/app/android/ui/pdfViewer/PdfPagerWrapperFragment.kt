package de.taz.app.android.ui.pdfViewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
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
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.ArticleOperations
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
import de.taz.app.android.ui.bottomSheet.ContinueReadBottomSheetFragment
import de.taz.app.android.ui.bottomSheet.SHOW_CONTINUE_READ_THE_SAME_NOT_MORE_THAN
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.drawer.DrawerViewController
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.issueViewer.IssueViewerWrapperFragment
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.showAlwaysTitleSectionSettingDialog
import de.taz.app.android.ui.showContinueReadSettingDialog
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.showIssueDownloadFailedDialog
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class PdfPagerWrapperFragment: ViewBindingFragment<ActivityPdfDrawerLayoutBinding>(), SuccessfulLoginAction, BackFragment {

    companion object {
        private const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"
        private const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE"
        private const val KEY_CONTINUE_READ_DIRECTLY = "KEY_CONTINUE_READ_DIRECTLY"

        const val ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME =
            "ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME"

        fun newInstance(
            issuePublication: IssuePublicationWithPages,
            displayableKey: String? = null,
            continueReadDirectly: Boolean = false,
        ) = PdfPagerWrapperFragment().apply {
            arguments = bundleOf(
                KEY_ISSUE_PUBLICATION to issuePublication,
                KEY_DISPLAYABLE to displayableKey,
                KEY_CONTINUE_READ_DIRECTLY to continueReadDirectly,
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
    private val continueReadDirectly: Boolean
        get() = arguments?.getBoolean(IssueViewerWrapperFragment.Companion.KEY_CONTINUE_READ_DIRECTLY) ?: false

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

        pdfPagerViewModel.setIssuePublication(issuePublication, continueReadDirectly)

        pdfPagerViewModel.issueDownloadFailedErrorFlow
            .filter { it }
            .asLiveData()
            .observe(this) {
                requireActivity().showIssueDownloadFailedDialog(issuePublication)
            }

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction().add(
                R.id.activity_pdf_fragment_placeholder,
                PdfPagerFragment()
            ).commit()
        }

        val displayableKey: String? = arguments?.getString(KEY_DISPLAYABLE)
        // I guess this is used by LMd or when clicked a notification with article link,
        // but it should not be triggered when [continueReadDirectly] is true
        if (displayableKey != null && !continueReadDirectly) {
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

                launch {
                    pdfPagerViewModel.continueReadDisplayable.filterNotNull().collect {
                        if (childFragmentManager.findFragmentByTag(
                                ContinueReadBottomSheetFragment.TAG
                            ) == null
                        ) {
                            if (continueReadDirectly) {
                                goDirectlyToDisplayable(it)
                            }
                            else {
                                ContinueReadBottomSheetFragment.newInstance(it).show(
                                    childFragmentManager, ContinueReadBottomSheetFragment.TAG
                                )
                            }
                        }
                    }
                }

                // Check whether maybe show dialog to always continue read or always show title page
                val askEachTimeToContinueRead =
                    generalDataStore.settingsContinueReadAskEachTime.get()
                val settingsDialogShown = generalDataStore.settingsContinueReadDialogShown.get()
                if (askEachTimeToContinueRead && !settingsDialogShown && !continueReadDirectly) {
                    launch {
                        generalDataStore.continueReadClicked.asFlow().distinctUntilChanged()
                            .collect {
                                if (it == SHOW_CONTINUE_READ_THE_SAME_NOT_MORE_THAN) {
                                    showContinueReadSettingDialog()
                                    generalDataStore.settingsContinueReadDialogShown.set(true)
                                }
                            }
                    }
                    launch {
                        generalDataStore.continueReadDismissed.asFlow().distinctUntilChanged()
                            .collect {
                                if (it == SHOW_CONTINUE_READ_THE_SAME_NOT_MORE_THAN) {
                                    showAlwaysTitleSectionSettingDialog()
                                    generalDataStore.settingsContinueReadDialogShown.set(true)
                                }
                            }
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
        // pdf mode always has burger icon
        drawerAndLogoViewModel.setBurgerIcon()

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

    private fun goDirectlyToDisplayable(displayable: IssueKeyWithDisplayableKey) {
        val isArticle = displayable.displayableKey.startsWith("art")
        if (isArticle) {
            pdfPagerViewModel.showArticle(
                displayable.displayableKey,
                displayable.issueKey
            )
        } else {
            pdfPagerViewModel.goToPdfPage(displayable.displayableKey)
        }
    }

    override fun onBackPressed(): Boolean {
        return popArticlePagerFragmentIfOpen()
    }
}