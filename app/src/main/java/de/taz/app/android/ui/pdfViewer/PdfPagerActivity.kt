package de.taz.app.android.ui.pdfViewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout.LayoutParams
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.fragment.app.commit
import androidx.lifecycle.*
import com.bumptech.glide.Glide
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.R
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.IssueWithPages
import de.taz.app.android.audioPlayer.AudioPlayerViewController
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.dataStore.GeneralDataStore
import de.taz.app.android.dataStore.TazApiCssDataStore
import de.taz.app.android.databinding.ActivityPdfDrawerLayoutBinding
import de.taz.app.android.monkey.getApplicationScope
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.singletons.AuthHelper
import de.taz.app.android.singletons.KeepScreenOnHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.SuccessfulLoginAction
import de.taz.app.android.ui.drawer.DrawerAndLogoViewModel
import de.taz.app.android.ui.drawer.DrawerViewController
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setBottomNavigationBackActivity
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.showIssueDownloadFailedDialog
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfPagerActivity : ViewBindingActivity<ActivityPdfDrawerLayoutBinding>(), SuccessfulLoginAction {

    companion object {
        private const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"
        private const val KEY_DISPLAYABLE = "KEY_DISPLAYABLE"

        private const val ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME =
            "ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME"
        private const val SUBSCRIPTION_ELAPSED_BOTTOMSHEET_TAG =
            "SUBSCRIPTION_ELAPSED_BOTTOMSHEET_TAG"

        fun newIntent(
            packageContext: Context,
            issuePublication: IssuePublicationWithPages,
            displayableKey: String? = null
        ) =
            Intent(packageContext, PdfPagerActivity::class.java).apply {
                putExtra(KEY_ISSUE_PUBLICATION, issuePublication)
                displayableKey?.let {
                    putExtra(KEY_DISPLAYABLE, it)
                }
            }
    }

    private val pdfPagerViewModel by viewModels<PdfPagerViewModel>()
    private val issueContentViewModel by viewModels<IssueViewerViewModel>()
    private val drawerAndLogoViewModel by viewModels<DrawerAndLogoViewModel>()

    private val log by Log

    @Suppress("unused")
    private val audioPlayerViewController = AudioPlayerViewController(this)

    private lateinit var storageService: StorageService
    private lateinit var issuePublication: IssuePublicationWithPages
    private lateinit var tazApiCssDataStore: TazApiCssDataStore
    private lateinit var generalDataStore: GeneralDataStore
    private lateinit var drawerViewController: DrawerViewController

    // mutable instance state
    private var navButton: Image? = null

    // region views
    private val drawerLogo by lazy { viewBinding.drawerLogo }
    private val pdfDrawerLayout by lazy { viewBinding.pdfDrawerLayout }
    private val drawerLogoWrapper by lazy { viewBinding.drawerLogoWrapper }
    private val navView by lazy { viewBinding.navView }

    // endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storageService = StorageService.getInstance(applicationContext)
        tazApiCssDataStore = TazApiCssDataStore.getInstance(applicationContext)
        generalDataStore = GeneralDataStore.getInstance(applicationContext)
        drawerViewController = DrawerViewController(
            this,
            pdfDrawerLayout,
            drawerLogoWrapper,
            navView,
            drawerAndLogoViewModel::setLogoHiddenState
        )

        issuePublication = try {
            intent.getParcelableExtra(KEY_ISSUE_PUBLICATION)
                ?: throw IllegalStateException("PdfPagerActivity needs to be started with KEY_ISSUE_KEY in Intent extras of type IssueKey")
        } catch (e: ClassCastException) {
            log.warn("Somehow we got IssuePublication instead of IssuePublicationWithPages, so we wrap it it", e)
            Sentry.captureException(e)
            IssuePublicationWithPages(
                intent.getParcelableExtra(KEY_ISSUE_PUBLICATION)!!
            )
        }

        pdfPagerViewModel.setIssuePublication(issuePublication)

        pdfPagerViewModel.issueDownloadFailedErrorFlow
            .filter { it }
            .asLiveData()
            .observe(this) {
                showIssueDownloadFailedDialog(issuePublication)
            }

        pdfPagerViewModel.navButton.observe(this) {
            if (it != null) {
                lifecycleScope.launch { showNavButton(it) }
            }
        }

        drawerLogo.setOnClickListener {
            drawerAndLogoViewModel.closeDrawer()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().add(
                R.id.activity_pdf_fragment_placeholder,
                PdfPagerFragment()
            ).commit()
        }

        val displayableKey: String? = intent?.getStringExtra(KEY_DISPLAYABLE)
        if (displayableKey != null) {
            intent.removeExtra(KEY_DISPLAYABLE)
            val issueObserver = object : Observer<IssueWithPages?> {
                override fun onChanged(issueWithPages: IssueWithPages?) {
                    if (issueWithPages != null) {
                        val issueKey = IssueKey(issueWithPages.issueKey)
                        lifecycleScope.launch {
                            showArticle(issueKey, displayableKey)
                        }
                        pdfPagerViewModel.issueLiveData.removeObserver(this)
                    }
                }
            }
            pdfPagerViewModel.issueLiveData.observe(this, issueObserver)
        }

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
                drawerAndLogoViewModel.openDrawer()
            }

            override fun onDrawerClosed(drawerView: View) {
                drawerAndLogoViewModel.closeDrawer()
            }

            override fun onDrawerStateChanged(newState: Int) = Unit
        })

        drawerLogoWrapper.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            pdfDrawerLayout.updateDrawerLogoBoundingBox(
                v.width,
                v.height
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // show bottom sheet if user's subscription is elapsed and the issue status is public
                launch {
                    pdfPagerViewModel.showSubscriptionElapsedFlow
                        .distinctUntilChanged()
                        .filter { it }.collect {
                            // Do not show th the bottom sheet if it is already shown
                            // (maybe because the activity was re-recreated and it was restored from the bundle)
                            if (supportFragmentManager.findFragmentByTag(
                                    SUBSCRIPTION_ELAPSED_BOTTOMSHEET_TAG
                                ) == null
                            ) {
                                // Prevent a crash that occurs if the show() method is called after onSaveInstanceState.
                                if (!supportFragmentManager.isStateSaved) {
                                    SubscriptionElapsedBottomSheetFragment().show(
                                        supportFragmentManager,
                                        SUBSCRIPTION_ELAPSED_BOTTOMSHEET_TAG
                                    )
                                }
                            }
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
                        KeepScreenOnHelper.toggleScreenOn(it, this@PdfPagerActivity)
                    }
                }

                launch {
                    drawerAndLogoViewModel.drawerState.collect {
                        drawerViewController.handleDrawerState(it)
                    }
                }
            }
        }

        supportFragmentManager.addOnBackStackChangedListener {
            // When the articlePagerFragment is popped from the backstack via the back functionality
            // the logo shall be hidden again
            if (supportFragmentManager.backStackEntryCount == 0) {
                drawerAndLogoViewModel.hideLogo()
            }
        }
    }

    override fun onLogInSuccessful(articleName: String) {
        // Launch the Activity restarting logic from the application scope to prevent it from being
        // accidentally canceled due the the activity being finished
        getApplicationScope().launch {
            // Restart the activity if this is *not* a Week/Wochentaz abo
            val authHelper = AuthHelper.getInstance(applicationContext)
            if (!authHelper.isLoginWeek.get()) {
                finish()
                startActivity(intent.putExtra(KEY_DISPLAYABLE, articleName))
            } else {
                finish()
                val toastHelper = ToastHelper.getInstance(applicationContext)
                toastHelper.showToast(R.string.toast_login_week, long = true)
            }
        }
    }

    private suspend fun showNavButton(navButton: Image) {
        if (this.navButton != navButton) {
            this.navButton = navButton
            val imageDrawable = withContext(Dispatchers.IO) {
                Glide
                    .with(this@PdfPagerActivity)
                    .load(storageService.getAbsolutePath(navButton))
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
                drawerLogo.apply {
                    setImageDrawable(imageDrawable)
                    updateLayoutParams<LayoutParams> {
                        width = logicalWidth.toInt()
                        height = logicalHeight.toInt()
                    }
                }
                pdfDrawerLayout.requestLayout()
            }
            // Update the clickable bounding box:
            pdfDrawerLayout.updateDrawerLogoBoundingBox(
                logicalWidth.toInt(),
                drawerLogoWrapper.height
            )
        }
    }

    fun popArticlePagerFragmentIfOpen() {
        supportFragmentManager.popBackStack(
            ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME,
            POP_BACK_STACK_INCLUSIVE
        )
    }
    override fun onResume() {
        super.onResume()
        setBottomNavigationBackActivity(this, BottomNavigationItem.Home)
    }

    override fun onDestroy() {
        super.onDestroy()
        setBottomNavigationBackActivity(null, BottomNavigationItem.Home)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (audioPlayerViewController.onBackPressed()) {
            return
        }

        super.onBackPressed()
        setBottomNavigationBackActivity(null, BottomNavigationItem.Home)
    }

    /**
     * May be used from child fragments to show an Article within the [ArticlePagerFragment]
     */
    fun showArticle(article: Article) {
        lifecycleScope.launch {
            pdfPagerViewModel.issue?.let { issueWithPages ->
                val issueKey = IssueKey(issueWithPages.issueKey)
                val displayableKey = article.key
                showArticle(issueKey, displayableKey)
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
        if (supportFragmentManager.findFragmentByTag(ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE) == null) {
            supportFragmentManager.commit {
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
        val context = this
        val color = ContextCompat.getColor(context, R.color.colorAccent)
        try {
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder().setToolbarColor(color).build()
                )
                .build()
                .apply { launchUrl(context, Uri.parse(url)) }
        } catch (e: ActivityNotFoundException) {
            val toastHelper = ToastHelper.getInstance(applicationContext)
            if (url.startsWith("mailto:")) {
                toastHelper.showToast(R.string.toast_no_email_client)
            } else {
                toastHelper.showToast(R.string.toast_unknown_error)
            }
        }
    }
}