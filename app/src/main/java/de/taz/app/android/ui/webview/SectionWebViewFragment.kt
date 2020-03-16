package de.taz.app.android.ui.webview

import android.view.MenuItem
import android.widget.TextView
import de.taz.app.android.R
import de.taz.app.android.api.models.Section
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.singletons.DateHelper

class SectionWebViewFragment : WebViewFragment<Section>(R.layout.fragment_webview_section),
    BackFragment {

    private val dateHelper: DateHelper = DateHelper.getInstance()

    override val viewModel = object : WebViewViewModel<Section>() {
        override val displayableKey: String? = displayable?.sectionFileName
    }

    companion object {
        fun createInstance(section: Section): WebViewFragment<Section> {
            val fragment = SectionWebViewFragment()
            fragment.viewModel.displayable = section
            return fragment
        }
    }

    override fun setHeader(displayable: Section) {
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
            R.id.bottom_navigation_action_size ->
                showFontSettingBottomSheet()
        }
    }

    override fun onBackPressed(): Boolean {
        return false
    }
}