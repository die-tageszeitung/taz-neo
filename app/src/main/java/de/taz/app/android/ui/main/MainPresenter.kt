package de.taz.app.android.ui.main

import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.archive.ArchiveFragment
import de.taz.app.android.ui.drawer.bookmarks.BookmarkDrawerFragment
import de.taz.app.android.ui.drawer.sectionList.SectionDrawerFragment
import de.taz.app.android.ui.login.LoginFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainPresenter: MainContract.Presenter, BasePresenter<MainContract.View, MainDataController>(
    MainDataController::class.java
) {

    override fun onViewCreated() {
        getView()?.let { view ->
            view.getLifecycleOwner().lifecycleScope.launch(Dispatchers.IO) {
                IssueRepository.getInstance().getLatestIssue()?.let { issue ->
                    viewModel?.setIssue(issue)
                }
            }
        }
    }


    override fun onItemClicked(imageView: ImageView) {
        getView()?.let {
            it.highlightDrawerIcon(imageView)

            when (imageView.id) {
                R.id.drawer_icon_content -> {
                    it.setDrawerTitle(R.string.navigation_drawer_icon_content)
                    it.showDrawerFragment(SectionDrawerFragment())
                }
                R.id.drawer_icon_home -> {
                    it.setDrawerTitle(R.string.navigation_drawer_icon_home)
                    // TODO
                    it.showMainFragment(ArchiveFragment())
                }
                R.id.drawer_icon_bookmarks -> {
                    it.setDrawerTitle(R.string.navigation_drawer_icon_bookmarks)
                    it.showDrawerFragment(BookmarkDrawerFragment())
                }
                R.id.drawer_icon_settings -> {
                    it.setDrawerTitle(R.string.navigation_drawer_icon_settings)
                    it.showMainFragment(LoginFragment())
                }
                R.id.drawer_icon_help -> {
                    it.setDrawerTitle(R.string.navigation_drawer_icon_help)
                    // TODO
                    it.showToast("should show help")
                }
            }
        }
    }

}