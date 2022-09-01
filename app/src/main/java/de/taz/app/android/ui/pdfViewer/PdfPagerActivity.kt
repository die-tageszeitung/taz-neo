package de.taz.app.android.ui.pdfViewer

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.viewModels
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import de.taz.app.android.ARTICLE_PAGER_FRAGMENT_FROM_PDF_MODE
import de.taz.app.android.R
import de.taz.app.android.api.models.Image
import de.taz.app.android.base.ViewBindingActivity
import de.taz.app.android.databinding.ActivityPdfDrawerLayoutBinding
import de.taz.app.android.persistence.repository.IssuePublicationWithPages
import de.taz.app.android.singletons.StorageService
import de.taz.app.android.ui.DRAWER_OVERLAP_OFFSET
import de.taz.app.android.ui.navigation.BottomNavigationItem
import de.taz.app.android.ui.navigation.setBottomNavigationBackActivity
import de.taz.app.android.util.showIssueDownloadFailedDialog
import io.sentry.Sentry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filter

const val LOGO_PEAK = 8
const val HIDE_LOGO_DELAY_MS = 200L
const val LOGO_ANIMATION_DURATION_MS = 300L

class PdfPagerActivity : ViewBindingActivity<ActivityPdfDrawerLayoutBinding>() {

    companion object {
        const val KEY_ISSUE_PUBLICATION = "KEY_ISSUE_PUBLICATION"
    }

    private var navButton: Image? = null
    private lateinit var issuePublication: IssuePublicationWithPages
    private val pdfPagerViewModel by viewModels<PdfPagerViewModel>()
    private lateinit var storageService: StorageService

    private val navButtonAlpha = 255f
    private var drawerLogoWidth = 0f

    // region views
    private val drawerLogo by lazy { viewBinding.drawerLogo }
    private val pdfDrawerLayout by lazy { viewBinding.pdfDrawerLayout }
    private val drawerLogoWrapper by lazy { viewBinding.drawerLogoWrapper }

    // endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        issuePublication = try {
            intent.getParcelableExtra(KEY_ISSUE_PUBLICATION)!!
        } catch (e: ClassCastException) {
            val hint =
                "Somehow we got IssuePublication instead of IssuePublicationWithPages, so we wrap it it"
            Sentry.captureException(e, hint)
            IssuePublicationWithPages(
                intent.getParcelableExtra(KEY_ISSUE_PUBLICATION)!!
            )
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


        pdfPagerViewModel.hideDrawerLogo.observe(this@PdfPagerActivity) { toHide ->
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
        }

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
}