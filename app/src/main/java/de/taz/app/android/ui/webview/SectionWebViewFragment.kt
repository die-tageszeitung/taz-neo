package de.taz.app.android.ui.webview

import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SectionWebViewFragment : WebViewFragment<Section>() {

    var section: Section? = null

    companion object {
        fun createInstance(section: Section): WebViewFragment<Section> {
            val fragment = SectionWebViewFragment()
            fragment.section = section
            return fragment
        }
    }

    override fun getWebViewDisplayable(): Section? {
        return section
    }

    override fun setWebViewDisplayable(displayable: Section?) {
        section = displayable
    }


    override val headerLayoutId: Int = R.layout.fragment_webview_header_section

    override val visibleItemIds = listOf(
        R.id.bottom_navigation_action_help,
        R.id.bottom_navigation_action_size
    )

    override fun configureHeader(): Job? {
        return section?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                section?.let { section ->
                    setHeader(section, section.issueStub)
                }
            }
        }
    }

    private fun setHeader(section: Section, issueStub: IssueStub) {
        activity?.apply {
            runOnUiThread {
                findViewById<TextView>(R.id.section)?.apply {
                    text = section.title
                }
                dateToLowerCaseString(issueStub.date)?.let {
                    findViewById<TextView>(R.id.issue_date)?.apply {
                        text = it
                    }
                }
            }
        }
    }


    private fun dateToLowerCaseString(date: String): String? {
        return SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).parse(date)?.let { issueDate ->
            SimpleDateFormat("EEEE, dd. MMMM yyyy", Locale.GERMANY).format(
                issueDate
            ).toLowerCase(Locale.getDefault())
        }
    }

}
