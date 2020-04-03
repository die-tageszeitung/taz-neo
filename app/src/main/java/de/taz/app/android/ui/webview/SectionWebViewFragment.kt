package de.taz.app.android.ui.webview

import android.graphics.Point
import android.graphics.Typeface
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.singletons.FileHelper
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Exception

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
                issueOperations.apply {
                    if (isWeekend) {
                        FileHelper.getInstance().getFile(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)?.let {
                            try {
                                val typeface = Typeface.createFromFile(it)
                                withContext(Dispatchers.Main) {
                                    view?.findViewById<TextView>(R.id.section)?.typeface = typeface
                                }
                            } catch (e: Exception) {
                                Sentry.capture(e)
                            }
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
            }
        }
    }

    override fun onResume() {
        activity?.findViewById<ImageView>(R.id.drawer_logo)?.let {
            resizeHeaderSectionTitle(it)
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
            resizeHeaderSectionTitle(v)
        }

    private fun resizeHeaderSectionTitle(drawerLogo: View) {
        // ensure the text is not shown below the logo
        setMaxSizeDependingOnDrawerLogo(R.id.section, drawerLogo)
        setMaxSizeDependingOnDrawerLogo(R.id.issue_date, drawerLogo)
    }

    private fun setMaxSizeDependingOnDrawerLogo(@IdRes viewId: Int, drawerLogo: View) {
        val point = Point()
        activity?.windowManager?.defaultDisplay?.getSize(point)
        view?.findViewById<TextView>(viewId)?.apply {
            val parentView = (parent as View)
            width =
                point.x - drawerLogo.width - parentView.marginLeft - parentView.marginRight - marginLeft - marginRight
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