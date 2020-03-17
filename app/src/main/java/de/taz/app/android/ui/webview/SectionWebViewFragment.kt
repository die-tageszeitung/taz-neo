package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModelProvider
import de.taz.app.android.R
import de.taz.app.android.api.models.Section
import de.taz.app.android.singletons.DateHelper
import kotlinx.android.synthetic.main.fragment_webview_section.*

class SectionWebViewFragment : WebViewFragment<Section>(R.layout.fragment_webview_section) {

    private val dateHelper: DateHelper = DateHelper.getInstance()

    override lateinit var viewModel: SectionWebViewViewModel
    private var displayableKey: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this).get(SectionWebViewViewModel::class.java)
        viewModel.displayableKey = displayableKey
        super.onViewCreated(view, savedInstanceState)

        web_view_wrapper.setOnScrollChangeListener { v: NestedScrollView?, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int ->
            viewModel.scrollPosition = scrollY
        }
    }

    companion object {
        fun createInstance(sectionFileName: String): WebViewFragment<Section> {
            val fragment = SectionWebViewFragment()
            fragment.displayableKey = sectionFileName
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

    override fun onPageFinishedLoading() {
        viewModel.scrollPosition?.let {
            web_view_wrapper.scrollY = it
        }
        super.onPageFinishedLoading()
    }
}