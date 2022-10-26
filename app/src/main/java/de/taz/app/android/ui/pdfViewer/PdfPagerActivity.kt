package de.taz.app.android.ui.pdfViewer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE
import androidx.fragment.app.commit
import androidx.lifecycle.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import com.bumptech.glide.Glide
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageType
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityPdfDrawerLayoutBinding
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.singletons.ToastHelper
import de.taz.app.android.ui.DRAWER_OVERLAP_OFFSET
import de.taz.app.android.ui.issueViewer.IssueKeyWithDisplayableKey
import de.taz.app.android.ui.issueViewer.IssueViewerViewModel
import de.taz.app.android.ui.login.fragments.SubscriptionElapsedBottomSheetFragment
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setBottomNavigationBackActivity
import de.taz.app.android.ui.webview.ImprintWebViewFragment
import de.taz.app.android.ui.webview.pager.ArticlePagerFragment
import de.taz.app.android.util.Log
import de.taz.app.android.util.showIssueDownloadFailedDialog
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfPagerActivity : ViewBindingActivity<ActivityPdfDrawerLayoutBinding>() {

    companion object {
        private const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"

        private const val LOGO_PEAK = 8
        private const val HIDE_LOGO_DELAY_MS = 200L
        private const val LOGO_ANIMATION_DURATION_MS = 300L
        private const val ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME =
            "ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME"
        private const val SUBSCRIPTION_ELAPSED_BOTTOMSHEET_TAG =
            "SUBSCRIPTION_ELAPSED_BOTTOMSHEET_TAG"

        // The drawer initialization will be delayed, so that the main pdf rendering has some time to finish
        private const val DRAWER_INIT_DELAY_MS = 10L

        fun newIntent(packageContext: Context, issuePublication: IssuePublicationWithPages) =
            Intent(packageContext, PdfPagerActivity::class.java).apply {
                putExtra(KEY_ISSUE_PUBLICATION, issuePublication)
            }
    }

    private val log by Log

    private val pdfPagerViewModel by viewModels<PdfPagerViewModel>()
    private val issueContentViewModel by viewModels<IssueViewerViewModel>()

    private lateinit var storageService: StorageService
    private lateinit var issuePublication: IssuePublicationWithPages

    // mutable instance state
    private var navButton: Image? = null
    private var drawerAdapter: PdfDrawerRecyclerViewAdapter? = null
    private var drawerLogoWidth = 0f

    // region views
    private val drawerLogo by lazy { viewBinding.drawerLogo }
    private val navigationRecyclerView by lazy { viewBinding.navigationRecyclerView }
    private val pdfDrawerLayout by lazy { viewBinding.pdfDrawerLayout }
    private val drawerLogoWrapper by lazy { viewBinding.drawerLogoWrapper }
    private val pdfDrawerLoadingScreen by lazy { viewBinding.pdfDrawerLoadingScreen }
    private val activityPdfDrawerFrontPage by lazy { viewBinding.activityPdfDrawerFrontPage }
    private val activityPdfDrawerFrontPageTitle by lazy { viewBinding.activityPdfDrawerFrontPageTitle }
    private val activityPdfDrawerDate by lazy { viewBinding.activityPdfDrawerDate }

    // endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storageService = StorageService.getInstance(applicationContext)

        issuePublication = try {
            intent.getParcelableExtra(KEY_ISSUE_PUBLICATION)
                ?: throw IllegalStateException("PdfPagerActivity needs to be started with KEY_ISSUE_KEY in Intent extras of type IssueKey")
        } catch (e: ClassCastException) {
            val hint =
                "Somehow we got IssuePublication instead of IssuePublicationWithPages, so we wrap it it"
            Sentry.captureException(e, hint)
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

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().add(
                R.id.activity_pdf_fragment_placeholder,
                PdfPagerFragment()
            ).commit()
        }

        // Add Item Touch Listener
        navigationRecyclerView.addOnItemTouchListener(
            RecyclerTouchListener(
                this@PdfPagerActivity,
                fun(_: View, drawerPosition: Int) {
                    log.debug("position clicked: $drawerPosition. pdf")
                    // currentItem.value begins from 0 to n-1th pdf page
                    // but in the drawer the front page is not part of the drawer list, that's why
                    // it needs to be incremented by 1:
                    val realPosition = drawerPosition + 1
                    if (realPosition != pdfPagerViewModel.currentItem.value) {
                        pdfPagerViewModel.updateCurrentItem(realPosition)
                        drawerAdapter?.activePosition = drawerPosition
                    }
                    popArticlePagerFragmentIfOpen()
                    pdfDrawerLayout.closeDrawers()
                }
            )
        )

        pdfDrawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                drawerLogoWrapper.animate().cancel()
                drawerLogoWrapper.translationX =
                    resources.getDimension(R.dimen.drawer_logo_translation_x)
                pdfDrawerLayout.updateDrawerLogoBoundingBox(
                    drawerLogoWrapper.width,
                    drawerLogoWrapper.height
                )
                (drawerView.parent as? View)?.let { parentView ->
                    val drawerWidth =
                        drawerView.width + (pdfDrawerLayout.drawerLogoBoundingBox?.width()
                            ?: 0)
                    if (parentView.width < drawerWidth) {
                        // translation needed for logo to be shown when drawer is too wide:
                        val offsetOnOpenDrawer =
                            slideOffset * (parentView.width - drawerWidth)
                        // translation needed when drawer is closed then:
                        val offsetOnClosedDrawer =
                            (1 - slideOffset) * DRAWER_OVERLAP_OFFSET * resources.displayMetrics.density
                        drawerLogoWrapper.translationX =
                            offsetOnOpenDrawer + offsetOnClosedDrawer
                    }
                }
            }

            override fun onDrawerClosed(drawerView: View) {
                pdfPagerViewModel.hideDrawerLogo.postValue(true)
            }

            override fun onDrawerOpened(drawerView: View) = Unit
            override fun onDrawerStateChanged(newState: Int) = Unit
        })

        pdfPagerViewModel.pdfPageList.observe(this@PdfPagerActivity) {
            initDrawerAdapterWithDelay(it)
        }

        pdfPagerViewModel.hideDrawerLogo.observe(this@PdfPagerActivity) { toHide ->
            if (toHide
                && !isArticlePagerFragmentOpen()
                && !pdfDrawerLayout.isDrawerOpen(GravityCompat.START)
            ) {
                hideDrawerLogoWithDelay()
            } else {
                showDrawerLogo()
            }
        }

        drawerLogoWrapper.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            pdfDrawerLayout.updateDrawerLogoBoundingBox(
                v.width,
                v.height
            )
        }

        // show bottom sheet  if  user's subscription is elapsed and the issue status is public
        lifecycleScope.launch {
            pdfPagerViewModel.showSubscriptionElapsedFlow
                .distinctUntilChanged()
                .filter { it }.collect {
                // Do not show th the bottom sheet if it is already shown
                // (maybe because the activity was re-recreated and it was restored from the bundle)
                if (supportFragmentManager.findFragmentByTag(SUBSCRIPTION_ELAPSED_BOTTOMSHEET_TAG) == null) {
                    SubscriptionElapsedBottomSheetFragment().show(supportFragmentManager, SUBSCRIPTION_ELAPSED_BOTTOMSHEET_TAG)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
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
        }
    }

    private fun hideDrawerLogoWithDelay() {
        if (pdfPagerViewModel.hideDrawerLogo.value == true) {
            val transX = -drawerLogoWidth + LOGO_PEAK * resources.displayMetrics.density
            drawerLogoWrapper.animate()
                .withEndAction {
                    pdfDrawerLayout.updateDrawerLogoBoundingBox(
                        (LOGO_PEAK * resources.displayMetrics.density).toInt(),
                        drawerLogoWrapper.height
                    )
                }
                .setDuration(LOGO_ANIMATION_DURATION_MS)
                .setStartDelay(HIDE_LOGO_DELAY_MS)
                .translationX(transX)
                .interpolator = AccelerateDecelerateInterpolator()
        }

    }

    private fun showDrawerLogo(hideAgainFlag: Boolean = true) {
        if (pdfPagerViewModel.hideDrawerLogo.value == false) {
            drawerLogoWrapper.animate()
                .withEndAction {
                    pdfDrawerLayout.updateDrawerLogoBoundingBox(
                        drawerLogoWidth.toInt(),
                        drawerLogoWrapper.height
                    )
                    if (hideAgainFlag) {
                        pdfPagerViewModel.hideDrawerLogo.postValue(true)
                    }
                }
                .setDuration(LOGO_ANIMATION_DURATION_MS)
                .setStartDelay(0L)
                .translationX(resources.getDimension(R.dimen.drawer_logo_translation_x))
                .interpolator = AccelerateDecelerateInterpolator()
        }

    }

    private fun initDrawerAdapterWithDelay(items: List<Page>) {
        lifecycleScope.launch {
            delay(DRAWER_INIT_DELAY_MS)
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                initDrawAdapter(items)
            }
        }
    }

    private fun initDrawAdapter(items: List<Page>) {
        if (items.isNotEmpty()) {
            // Setup a gridManager which takes 2 columns for panorama pages
            val gridLayoutManager = GridLayoutManager(this@PdfPagerActivity, 2)
            gridLayoutManager.spanSizeLookup = object : SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (items[position + 1].type == PageType.panorama) {
                        2
                    } else {
                        1
                    }
                }
            }

            // Setup Recyclerview's Layout
            navigationRecyclerView.apply {
                layoutManager = gridLayoutManager
                setHasFixedSize(false)
            }

            // Setup drawer header (front page and date)
            Glide
                .with(this@PdfPagerActivity)
                .load(storageService.getAbsolutePath(items.first().pagePdf))
                .into(activityPdfDrawerFrontPage)

            activityPdfDrawerFrontPage.setOnClickListener {
                val newPosition = 0
                if (newPosition != pdfPagerViewModel.currentItem.value) {
                    pdfPagerViewModel.updateCurrentItem(newPosition)
                    drawerAdapter?.activePosition = newPosition
                }
                popArticlePagerFragmentIfOpen()
                activityPdfDrawerFrontPageTitle.setTextColor(
                    ContextCompat.getColor(
                        this@PdfPagerActivity,
                        R.color.pdf_drawer_sections_item_highlighted
                    )
                )
                pdfDrawerLayout.closeDrawers()
            }
            activityPdfDrawerFrontPageTitle.apply {
                text = items.first().title
                setTextColor(
                    ContextCompat.getColor(
                        this@PdfPagerActivity,
                        R.color.pdf_drawer_sections_item_highlighted
                    )
                )
            }
            activityPdfDrawerDate.text =
                DateHelper.stringToLongLocalized2LineString(issuePublication.date)

            drawerAdapter =
                PdfDrawerRecyclerViewAdapter(
                    items.subList(1, items.size),
                    Glide.with(this@PdfPagerActivity)
                )

            pdfPagerViewModel.currentItem.observe(this@PdfPagerActivity) { position ->
                drawerAdapter?.activePosition = position - 1
                if (position > 0) {
                    log.debug("set front page title color to: ${R.color.pdf_drawer_sections_item}")
                    activityPdfDrawerFrontPageTitle.setTextColor(
                        ContextCompat.getColor(
                            this@PdfPagerActivity,
                            R.color.pdf_drawer_sections_item
                        )
                    )
                }
            }
            navigationRecyclerView.adapter = drawerAdapter
            hideLoadingScreen()
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

            drawerLogoWidth = logicalWidth
            val logicalHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                imageDrawable.intrinsicHeight.toFloat(),
                resources.displayMetrics
            ) * scaleFactor

            withContext(Dispatchers.Main) {
                drawerLogo.apply {
                    setImageDrawable(imageDrawable)
                    layoutParams.width = logicalWidth.toInt()
                    layoutParams.height = logicalHeight.toInt()
                }
                drawerLogoWrapper.apply {
                    layoutParams.width = logicalWidth.toInt()
                    layoutParams.height = logicalHeight.toInt()
                    translationX =
                        resources.getDimension(R.dimen.drawer_logo_translation_x)
                    requestLayout()
                }
                pdfDrawerLayout.requestLayout()
            }
            // Update the clickable bounding box:
            pdfDrawerLayout.updateDrawerLogoBoundingBox(
                drawerLogoWidth.toInt(),
                drawerLogoWrapper.height
            )

        }
        if (!pdfDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            pdfPagerViewModel.hideDrawerLogo.postValue(true)
        } else
            pdfPagerViewModel.hideDrawerLogo.postValue(false)
    }

    private fun hideLoadingScreen() {
        this.runOnUiThread {
            pdfDrawerLoadingScreen.root.apply {
                animate()
                    .alpha(0f)
                    .withEndAction {
                        this.visibility = View.GONE
                    }
                    .duration = LOADING_SCREEN_FADE_OUT_TIME
            }
        }
    }

    private fun popArticlePagerFragmentIfOpen() {
        supportFragmentManager.popBackStack(
            ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME,
            POP_BACK_STACK_INCLUSIVE
        )
    }

    private fun isArticlePagerFragmentOpen(): Boolean {
        val articlePagerFragment =
            supportFragmentManager.findFragmentByTag(ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE)
        return articlePagerFragment != null
    }

    override fun onResume() {
        super.onResume()
        setBottomNavigationBackActivity(this, BottomNavigationItem.Home)
    }

    override fun onDestroy() {
        super.onDestroy()
        setBottomNavigationBackActivity(null, BottomNavigationItem.Home)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        setBottomNavigationBackActivity(null, BottomNavigationItem.Home)
    }

    private suspend fun showArticle(issueKey: IssueKey, displayableKey: String?) {
        issueContentViewModel.setDisplayable(issueKey, displayableKey)
        showArticlePagerFragment(ArticlePagerFragment())
    }

    private fun showImprint(issueKeyWithDisplayableKey: IssueKeyWithDisplayableKey) {
        issueContentViewModel.setDisplayable(issueKeyWithDisplayableKey)
        showArticlePagerFragment(ImprintWebViewFragment())
    }

    private fun showArticlePagerFragment(fragment: Fragment) {
        supportFragmentManager.commit {
            add(
                R.id.activity_pdf_fragment_placeholder,
                fragment,
                ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
            )
            addToBackStack(ARTICLE_PAGER_FRAGMENT_BACKSTACK_NAME)
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