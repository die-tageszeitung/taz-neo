package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    override val inactiveIconMap = mapOf(
        R.id.bottom_navigation_action_bookmark to R.drawable.ic_bookmark,
        R.id.bottom_navigation_action_share to R.drawable.ic_share,
        R.id.bottom_navigation_action_size to R.drawable.ic_text_size
    )

    override val activeIconMap = mapOf(
        R.id.bottom_navigation_action_bookmark to R.drawable.ic_bookmark_active,
        R.id.bottom_navigation_action_share to R.drawable.ic_share_active,
        R.id.bottom_navigation_action_size to R.drawable.ic_text_size_active
    )

    companion object {
        fun createInstance(section: Section): WebViewFragment<Section> {
            val fragment = SectionWebViewFragment()
            fragment.section = section
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview_section, container, false)
    }

    override fun getWebViewDisplayable(): Section? {
        return section
    }

    override fun setWebViewDisplayable(displayable: Section?) {
        section = displayable
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        section?.let {
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
