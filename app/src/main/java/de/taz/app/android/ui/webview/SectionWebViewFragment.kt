package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import de.taz.app.android.R
import de.taz.app.android.api.models.Section
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.singletons.DateHelper

class SectionWebViewFragment : WebViewFragment<Section>(R.layout.fragment_webview_section) {

    private val dateHelper: DateHelper = DateHelper.getInstance()

    override lateinit var viewModel: SectionWebViewViewModel
    private var displayableKey: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewModel = ViewModelProvider(this).get(SectionWebViewViewModel::class.java)
        viewModel.displayableKey = displayableKey
        return super.onCreateView(inflater, container, savedInstanceState)
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

}