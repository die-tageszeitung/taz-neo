package de.taz.app.android.ui.drawer.sectionList

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.ui.main.MainActivity
import kotlinx.android.synthetic.main.fragment_drawer_sections.*

/**
 * Fragment used to display the list of sections in the navigation Drawer
 */
class SectionDrawerFragment : Fragment(R.layout.fragment_drawer_sections) {

    private val recyclerAdapter = SectionListAdapter(this)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        fragment_drawer_sections_moment.setOnClickListener {
            getMainView()?.showHome()
            getMainView()?.closeDrawer()
        }

        fragment_drawer_sections_list.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@SectionDrawerFragment.context)
            adapter = recyclerAdapter
        }
    }

    fun getMainView(): MainActivity? {
        return activity as? MainActivity
    }

    fun setIssueStub(issueOperations: IssueOperations) {
       recyclerAdapter.setData(issueOperations)
    }

}
