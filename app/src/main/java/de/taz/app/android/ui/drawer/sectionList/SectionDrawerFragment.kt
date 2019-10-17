package de.taz.app.android.ui.drawer.sectionList

import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.R
import de.taz.app.android.ui.main.MainDataController
import kotlinx.android.synthetic.main.fragment_drawer_menu_sections.*

/**
 * Fragment used to display the list of sections in the navigation Drawer
 */
class SectionDrawerFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_drawer_menu_sections, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val viewModel = ViewModelProviders.of(requireActivity()).get(MainDataController::class.java)

        val recycleAdapter =
            SectionListAdapter(requireActivity() as MainActivity)
        drawer_menu_list.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@SectionDrawerFragment.context)
            adapter = recycleAdapter
        }

        viewModel.observeIssueIsDownloaded(this) {
            recycleAdapter.setData(viewModel.getIssue())
        }
    }

}
