package de.taz.app.android.ui.webview

import android.view.MenuItem
import android.widget.TextView
import de.taz.app.android.R
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.singletons.DateHelper

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