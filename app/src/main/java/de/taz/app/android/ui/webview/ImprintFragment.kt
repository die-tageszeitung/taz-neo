package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.WEEKEND_TYPEFACE_RESOURCE_FILE_NAME
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.singletons.FontHelper
import de.taz.app.android.ui.BackFragment
import de.taz.app.android.ui.bottomSheet.textSettings.TextSettingsFragment
import de.taz.app.android.ui.webview.pager.IssueContentFragment
import de.taz.app.android.ui.webview.pager.IssueContentViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImprintFragment :
    WebViewFragment<ArticleStub, WebViewViewModel<ArticleStub>>(R.layout.fragment_webview_imprint),
    BackFragment {

    override val nestedScrollViewId = R.id.nested_scroll_view

    override val bottomNavigationMenuRes = R.menu.navigation_bottom_section
    override val viewModel: ArticleWebViewViewModel by lazy {
        ViewModelProvider(this).get(ArticleWebViewViewModel::class.java)
    }

    private val issueContentViewModel: IssueContentViewModel? by lazy {
        (parentFragment as? IssueContentFragment)?.viewModel
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        displayable = issueContentViewModel?.imprint
        super.onCreate(savedInstanceState)
    }

    override fun setHeader(displayable: ArticleStub) {
        lifecycleScope.launch(Dispatchers.IO) {

            val title = getString(R.string.imprint)
            activity?.runOnUiThread {
                view?.findViewById<TextView>(R.id.section)?.apply {
                    text = title
                    setOnClickListener {
                        showHome()
                    }
                }
            }

            val issueOperations = displayable.getIssueOperations(context?.applicationContext)
            issueOperations?.apply {
                if (isWeekend) {
                    FontHelper.getInstance(context?.applicationContext)
                        .getTypeFace(WEEKEND_TYPEFACE_RESOURCE_FILE_NAME)?.let { typeface ->
                            withContext(Dispatchers.Main) {
                                view?.findViewById<TextView>(R.id.section)?.typeface = typeface
                                view?.findViewById<TextView>(R.id.article_num)?.typeface = typeface
                            }
                        }
                }
            }
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_home -> {
                showHome(skipToNewestIssue = true)
            }
            R.id.bottom_navigation_action_size -> {
                showBottomSheet(TextSettingsFragment())
            }
        }
    }

    override fun onBackPressed(): Boolean {
        showHome()
        return true
    }
}