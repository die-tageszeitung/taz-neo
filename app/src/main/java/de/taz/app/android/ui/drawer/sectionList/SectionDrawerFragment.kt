package de.taz.app.android.ui.drawer.sectionList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import de.taz.app.android.R
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.main.MainDataController
import kotlinx.android.synthetic.main.fragment_drawer_sections.*

/**
 * Fragment used to display the list of sections in the navigation Drawer
 */
class SectionDrawerFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_drawer_sections, container, false)
    }

    private val recyclerAdapter = SectionListAdapter(this)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val viewModel = ViewModelProviders.of(requireActivity()).get(MainDataController::class.java)

        viewModel.observeIssueStub(viewLifecycleOwner) { issueStub ->
            recyclerAdapter.setData(issueStub)
        }

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

}
