package de.taz.app.android.ui.drawer

import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.taz.app.android.R
import kotlinx.android.synthetic.main.fragment_drawer_menu_list.*
import de.taz.app.android.api.models.Issue


class SectionListFragment : Fragment() {

    private lateinit var viewModel: SelectedIssueViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_drawer_menu_list, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity()).get(SelectedIssueViewModel::class.java)

        val recycleAdapter = MyAdapter()
        drawer_menu_list.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@SectionListFragment.context)
            adapter = recycleAdapter
        }
        viewModel.selectedIssue.observe(this, Observer { issue ->
            recycleAdapter.setData(issue)
            activity?.let {
                it.findViewById<TextView>(R.id.drawerDateText).text = issue?.date ?: ""
            }
// TODO            activity?.findViewById<ImageView>(R.id.drawerMoment)
        })
    }

}

class MyAdapter(private var issue: Issue? = null): RecyclerView.Adapter<MyAdapter.MyViewHolder>() {

    fun setData(newIssue: Issue?) {
        this.issue = newIssue
        notifyDataSetChanged()
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder.
    // Each data item is just a string in this case that is shown in a TextView.
    class MyViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)


    // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): MyViewHolder {
        // create a new view
        val textView = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_drawer_menu_list_item, parent, false) as TextView
        return MyViewHolder(textView)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        holder.textView.text = issue?.sectionList?.get(position)?.title
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount() = issue?.sectionList?.size ?: 0
}
