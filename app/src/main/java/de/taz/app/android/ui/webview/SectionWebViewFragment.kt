package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.FileEntry
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.api.models.Section
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.util.DateHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SectionWebViewFragment : WebViewFragment<Section>(), BackFragment {

    var section: Section? = null
    private val dateHelper: DateHelper = DateHelper.getInstance()

    override val presenter = SectionWebViewPresenter()

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

    override fun share(url: String, title: String?, image: FileEntry?) {

    }

    private fun setHeader(section: Section, issueStub: IssueStub) {
        activity?.apply {
            runOnUiThread {
                view?.findViewById<TextView>(R.id.section)?.apply {
                    text = section.getHeaderTitle()
                }
                dateHelper.dateToLowerCaseString(issueStub.date)?.let {
                    view?.findViewById<TextView>(R.id.issue_date)?.apply {
                        text = it
                    }
                }
            }
        }
    }

    override fun onBackPressed(): Boolean {
        return false
    }
}
