package de.taz.app.android.ui.archive

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.base.BaseFragment
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.ui.main.MainContract
import kotlinx.android.synthetic.main.fragment_archive.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Dispatcher

class ArchiveFragment : BaseFragment<ArchiveContract.Presenter>(), ArchiveContract.View {

    override val presenter = ArchivePresenter()
    val archiveListAdapter = ArchiveListAdapter(this)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_archive, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        presenter.attach(this)

        fragment_archive_swipe_refresh.setOnRefreshListener {
            presenter.onRefresh()
        }

        fragment_archive_grid.adapter = archiveListAdapter

        presenter.onViewCreated()


        fragment_archive_grid.setOnItemClickListener { _, _, position, _ ->
            presenter.onItemSelected(archiveListAdapter.getItem(position))
        }

        fragment_archive_grid.setOnScrollListener(ArchiveOnScrollListener(this))

    }

    override fun onDataSetChanged(issueStubs: List<IssueStub>) {
        (fragment_archive_grid?.adapter as? ArchiveListAdapter)?.setIssueStubs(issueStubs)
    }

    override fun hideScrollView() {
        fragment_archive_swipe_refresh.isRefreshing = false
    }

    override fun getMainView(): MainContract.View? {
        return activity as? MainActivity
    }

    override fun addMoment(tag: String, bitmap: Bitmap) {
        archiveListAdapter.addMomentBitmap(tag, bitmap)
        lifecycleScope.launch(Dispatchers.Main) {
            view?.findViewWithTag<View>(tag)?.apply {
                findViewById<ImageView>(R.id.fragment_archive_moment_image).apply {
                    setImageBitmap(bitmap)
                    visibility = View.VISIBLE

                }
                findViewById<View>(R.id.fragment_archive_moment_image_progressbar).visibility =
                    View.GONE
            }
        }
    }
}