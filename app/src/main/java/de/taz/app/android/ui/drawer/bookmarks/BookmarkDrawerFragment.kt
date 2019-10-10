package de.taz.app.android.ui.drawer.bookmarks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import de.taz.app.android.MainActivity
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_drawer_menu_bookmarks.*

class BookmarkDrawerFragment: Fragment() {

    private lateinit var viewModel: BookmarksViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_drawer_menu_bookmarks, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity()).get(BookmarksViewModel::class.java)

        val recycleAdapter =
            BookmarkListAdapter(requireActivity() as MainActivity)
        drawer_menu_list.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@BookmarkDrawerFragment.context)
            adapter = recycleAdapter
        }
        viewModel.bookmarkedArticleBases.observe(this, Observer { bookmarks ->
            recycleAdapter.setData(bookmarks ?: emptyList())
// TODO            activity?.findViewById<ImageView>(R.id.drawerMoment)
        })
    }

}