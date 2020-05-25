package de.taz.app.android.ui.webview

import android.graphics.Point
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FontHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SectionWebViewFragment : WebViewFragment<SectionStub>(R.layout.fragment_webview_section) {

    private val dateHelper: DateHelper = DateHelper.getInstance()
    override val viewModel = WebViewViewModel<SectionStub>()
    override val nestedScrollViewId: Int = R.id.web_view_wrapper

    companion object {
        fun createInstance(section: SectionStub): SectionWebViewFragment {
            val fragment = SectionWebViewFragment()
            fragment.displayable = section
            return fragment
        }
    }

    override fun setHeader(displayable: SectionStub) {
        activity?.apply {

            lifecycleScope.launch(Dispatchers.IO) {
                val issueOperations = displayable.getIssueOperations()
                issueOperations?.apply {
                    if (isWeekend) {
                        withContext(Dispatchers.Main) {
                            view?.findViewById<TextView>(R.id.section)?.typeface =
                                FontHelper.getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)
                            //following two lines align header/dotted line in weekend issues
                            //with text in taz logo; TODO check whether we can get rid of them later
                            view?.findViewById<AppBarLayout>(R.id.app_bar_layout)?.translationY =
                                18f
                            view?.findViewById<AppWebView>(R.id.web_view)?.translationY = 18f
                        }
                    }
                }
            }

            runOnUiThread {
                view?.findViewById<TextView>(R.id.section)?.apply {
                    text = displayable.getHeaderTitle()
                }
                dateHelper.dateToLowerCaseString(displayable.issueDate)?.let {
                    view?.findViewById<TextView>(R.id.issue_date)?.apply {
                        text = it
                    }
                }
                activity?.findViewById<ImageView>(R.id.drawer_logo)?.let {
                    resizeHeaderSectionTitle(it.width)
                }
            }
        }
    }

    override fun onResume() {
        activity?.findViewById<ImageView>(R.id.drawer_logo)?.let {
            resizeHeaderSectionTitle(it.width)
            it.addOnLayoutChangeListener(resizeDrawerLogoListener)
        }
        super.onResume()
    }

    override fun onPause() {
        activity?.findViewById<ImageView>(R.id.drawer_logo)?.removeOnLayoutChangeListener(
            resizeDrawerLogoListener
        )
        super.onPause()
    }

    private val resizeDrawerLogoListener =
        View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            resizeHeaderSectionTitle(v.width)
        }

    /**
     * ensure the text is not shown below the drawerLogo
     * @param drawerLogoWidth: Int - the width of the current logo shown in the drawer
     */
    private fun resizeHeaderSectionTitle(drawerLogoWidth: Int) {
        setMaxSizeDependingOnDrawerLogo(R.id.section, drawerLogoWidth)
        setMaxSizeDependingOnDrawerLogo(R.id.issue_date, drawerLogoWidth)
    }

    private fun setMaxSizeDependingOnDrawerLogo(@IdRes viewId: Int, drawerLogoWidth: Int) {
        val point = Point()
        activity?.windowManager?.defaultDisplay?.getSize(point)
        view?.findViewById<TextView>(viewId)?.apply {
            val parentView = (parent as View)
            width = point.x - drawerLogoWidth - parentView.marginRight - marginLeft - marginRight
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                showHome()
            }
            R.id.bottom_navigation_action_size -> {
                showFontSettingBottomSheet()
            }
        }
    }
}