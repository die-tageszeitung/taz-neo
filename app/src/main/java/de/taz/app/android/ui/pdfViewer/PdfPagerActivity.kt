package de.taz.app.android.ui.pdfViewer

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import com.bumptech.glide.Glide
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.LOADING_SCREEN_FADE_OUT_TIME
import de.taz.app.android.R
import de.taz.app.android.api.models.Image
import de.taz.app.android.api.models.Page
import de.taz.app.android.api.models.PageType
import de.taz.app.android.base.NightModeViewBindingActivity
import de.taz.app.android.databinding.ActivityPdfDrawerLayoutBinding
import de.taz.app.android.monkey.*
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.DRAWER_OVERLAP_OFFSET
import de.taz.app.android.util.Log
import de.taz.app.android.util.showIssueDownloadFailedDialog
import io.ktor.util.reflect.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter

const val LOGO_PEAK = 8
const val HIDE_LOGO_DELAY_MS = 1000L
const val LOGO_ANIMATION_DURATION_MS = 500L

class PdfPagerActivity : NightModeViewBindingActivity<ActivityPdfDrawerLayoutBinding>() {

    companion object {
        const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"
    }

    private val log by Log

    private var navButton: Image? = null
    private lateinit var issuePublication: IssuePublicationWithPages
    private val pdfPagerViewModel by viewModels<PdfPagerViewModel>()
    private lateinit var storageService: StorageService

    private val navButtonAlpha = 255f
    private lateinit var drawerAdapter: PdfDrawerRecyclerViewAdapter
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
        issuePublication = try {
            intent.getParcelableExtra(KEY_ISSUE_PUBLICATION)!!
        } catch (e: NullPointerException) {
            throw IllegalStateException("PdfPagerActivity needs to be started with KEY_ISSUE_KEY in Intent extras of type IssueKey")
        }

        pdfPagerViewModel.issueDownloadFailedErrorFlow
            .filter { it }
            .asLiveData()
            .observe(this) {
                showIssueDownloadFailedDialog(issuePublication)
            }

        if (savedInstanceState == null) {
            pdfPagerViewModel.issuePublication.postValue(issuePublication)
        }


        storageService = StorageService.getInstance(applicationContext)

        pdfPagerViewModel.navButton.observe(this) {
            if (it != null) {
                lifecycleScope.launch { showNavButton(it) }
            }
        }

        if (supportFragmentManager.findFragmentByTag(ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE) == null) {
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
                    val isFrontPage = drawerPosition == 0
                    if (realPosition != pdfPagerViewModel.currentItem.value || isFrontPage) {
                        pdfPagerViewModel.updateCurrentItem(realPosition)
                        drawerAdapter.activePosition = drawerPosition
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

        pdfPagerViewModel.pdfPageList.observe(this@PdfPagerActivity, {
            initDrawerAdapter(it)
        })

        pdfPagerViewModel.hideDrawerLogo.observe(this@PdfPagerActivity, { toHide ->
            val articlePagerFragment =
                supportFragmentManager.findFragmentByTag(ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE)
            if (toHide && articlePagerFragment == null && !pdfDrawerLayout.isDrawerOpen(
                    GravityCompat.START
                )
            ) {
                hideDrawerLogoWithDelay()
            } else {
                showDrawerLogo()
            }
        })

        drawerLogoWrapper.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            pdfDrawerLayout.updateDrawerLogoBoundingBox(
                v.width,
                v.height
            )
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

    private fun initDrawerAdapter(items: List<Page>) {
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
                popArticlePagerFragmentIfOpen()
                activityPdfDrawerFrontPageTitle.setTextColor(
                    ContextCompat.getColor(
                        this@PdfPagerActivity,
                        R.color.drawer_sections_item_highlighted
                    )
                )
                pdfDrawerLayout.closeDrawers()
            }
            activityPdfDrawerFrontPageTitle.apply {
                text = items.first().title
                setTextColor(
                    ContextCompat.getColor(
                        this@PdfPagerActivity,
                        R.color.drawer_sections_item_highlighted
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
            pdfPagerViewModel.currentItem.observe(this@PdfPagerActivity, { position ->
                drawerAdapter.activePosition = position - 1
                if (position > 0) {
                    log.debug("set front page title color to: ${R.color.drawer_sections_item}")
                    activityPdfDrawerFrontPageTitle.setTextColor(
                        ContextCompat.getColor(
                            this@PdfPagerActivity,
                            R.color.drawer_sections_item
                        )
                    )
                }
            })
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
                    alpha = navButtonAlpha
                    imageAlpha = (navButton.alpha * 255).toInt()
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
        val articlePagerFragment =
            supportFragmentManager.findFragmentByTag(ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE)
        if (articlePagerFragment != null && articlePagerFragment.isVisible) {
            supportFragmentManager.popBackStack()
        }
    }
}