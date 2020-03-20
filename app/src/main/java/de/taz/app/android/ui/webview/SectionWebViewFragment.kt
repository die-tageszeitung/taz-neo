package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import de.taz.app.android.R
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.singletons.DateHelper
import kotlinx.android.synthetic.main.fragment_webview_section.*

class SectionWebViewFragment : WebViewFragment<SectionStub>(R.layout.fragment_webview_section) {

    private val dateHelper: DateHelper = DateHelper.getInstance()

    override val viewModel = WebViewViewModel<SectionStub>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel.displayable = displayable
        super.onViewCreated(view, savedInstanceState)

        web_view_wrapper.setOnScrollChangeListener { _: NestedScrollView?, _: Int, scrollY: Int, _: Int, _: Int ->
            viewModel.scrollPosition = scrollY
        }
    }

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

    // TODO scroll to position if not visible as well...
    override fun onPageFinishedLoading() {
        super.onPageFinishedLoading()
        viewModel.scrollPosition?.let {
            web_view_wrapper?.scrollY = it
        } ?: app_bar_layout?.setExpanded(true, false)
    }
}