package de.taz.app.android.ui.pdfViewer

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import de.taz.app.android.DEFAULT_NAV_DRAWER_FILE_NAME
import de.taz.app.android.R
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.Image
import de.taz.app.android.base.NightModeActivity
import de.taz.app.android.monkey.*
import de.taz.app.android.persistence.repository.IssueKey
import de.taz.app.android.ui.main.MainActivity.Companion.KEY_ISSUE_KEY
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.activity_pdf_drawer_layout.*
import kotlinx.android.synthetic.main.fragment_pdf_pager.*
import kotlinx.coroutines.*
import kotlin.math.min

class PdfPagerActivity : NightModeActivity(R.layout.activity_pdf_drawer_layout) {
    private val log by Log

    private lateinit var issueKey: IssueKey
    private lateinit var pdfPagerViewModel: PdfPagerViewModel

    private var navButton: Image? = null
    private var navButtonAlpha = 255f
    lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerAdapter: PdfDrawerRecyclerViewAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        issueKey = try {
            intent.getParcelableExtra(KEY_ISSUE_KEY)!!
        } catch (e: NullPointerException) {
            throw IllegalStateException("PdfPagerActivity needs to be started with KEY_ISSUE_KEY in Intent extras of type IssueKey")
        }

        supportFragmentManager.beginTransaction().add(
            R.id.activity_pdf_fragment_placeholder,
            PdfPagerFragment()
        ).commit()

        pdfPagerViewModel = getViewModel { PdfPagerViewModel(application, issueKey) }

        drawerLayout = findViewById(R.id.pdf_drawer_layout)

        // Setup Recyclerview's Layout
        navigation_recycler_view.layoutManager = GridLayoutManager(this, 2)
        navigation_recycler_view.setHasFixedSize(true)

        // Add Item Touch Listener
        navigation_recycler_view.addOnItemTouchListener(
            RecyclerTouchListener(
                this,
                fun(_: View, position: Int) {
                    if (position != pdfPagerViewModel.currentItem.value) {
                        pdfPagerViewModel.currentItem.value = position
                        pdf_drawer_layout.closeDrawers()
                        drawerAdapter.activePosition = position
                    }
                }
            )
        )

        drawer_logo.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            log.debug("updateDrawerLogo. width: ${v.width} height: ${v.height}")
            pdf_drawer_layout.updateDrawerLogoBoundingBox(
                v.width,
                v.height
            )
        }

        pdf_drawer_layout.addDrawerListener(object : DrawerLayout.DrawerListener {
            var opened = false

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                (drawerView.parent as? View)?.let { parentView ->
                    val drawerWidth =
                        drawerView.width + (pdf_drawer_layout.drawerLogoBoundingBox?.width() ?: 0)
                    if (parentView.width < drawerWidth) {
                        drawer_logo.translationX = min(
                            slideOffset * (parentView.width - drawerWidth),
                            -5f * resources.displayMetrics.density
                        )
                    }
                }
            }

            override fun onDrawerOpened(drawerView: View) {
                log.debug("drawer opened!")
                opened = true
            }

            override fun onDrawerClosed(drawerView: View) {
                log.debug("drawer closed!")
                opened = false
            }

            override fun onDrawerStateChanged(newState: Int) = Unit
        })
        pdfPagerViewModel.pdfDataListModel.observe(this, {
            initDrawerAdapter(it)
        })
    }

    override fun onResume() {
        super.onResume()
        pdfPagerViewModel.setDefaultDrawerNavButton()
        pdfPagerViewModel.navButton.observeDistinct(this) {
            lifecycleScope.launch(Dispatchers.IO) {
                val baseUrl =
                    pdfPagerViewModel.dataService.getResourceInfo(retryOnFailure = true).resourceBaseUrl
                if (it != null) {
                    pdfPagerViewModel.dataService.ensureDownloaded(FileEntry(it), baseUrl)
                    showNavButton(it)
                } else {
                    pdfPagerViewModel.imageRepository.get(DEFAULT_NAV_DRAWER_FILE_NAME)
                        ?.let { image ->
                            pdfPagerViewModel.dataService.ensureDownloaded(
                                FileEntry(image),
                                baseUrl
                            )
                            showNavButton(
                                image
                            )
                        }
                }
            }
        }
    }

    private fun initDrawerAdapter(items: List<PdfPageListModel>) {
        drawerAdapter = PdfDrawerRecyclerViewAdapter(items)
        pdfPagerViewModel.activePosition.observe(this, { position ->
            drawerAdapter.activePosition = position
        })
        navigation_recycler_view.adapter = drawerAdapter
    }

    private suspend fun showNavButton(navButton: Image) {
        if (this.navButton != navButton) {
            this.navButton = navButton
            val imageDrawable = withContext(Dispatchers.IO) {
                Glide
                    .with(this@PdfPagerActivity)
                    .load(pdfPagerViewModel.storageService.getAbsolutePath(navButton))
                    .submit()
                    .get()
            }

            val scaleFactor = 1f / 3f
            val logicalWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                imageDrawable.intrinsicWidth.toFloat(),
                resources.displayMetrics
            ) * scaleFactor

            val logicalHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                imageDrawable.intrinsicHeight.toFloat(),
                resources.displayMetrics
            ) * scaleFactor

            withContext(Dispatchers.Main) {
                drawer_logo.setImageDrawable(imageDrawable)
                drawer_logo.alpha = navButtonAlpha
                drawer_logo.imageAlpha = (navButton.alpha * 255).toInt()
                drawer_logo.layoutParams.width = logicalWidth.toInt()
                drawer_logo.layoutParams.height = logicalHeight.toInt()
                pdf_drawer_layout.requestLayout()
            }
        }
    }
}