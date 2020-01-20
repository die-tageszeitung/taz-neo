package de.taz.app.android.ui.home

import android.view.MenuItem
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.base.BasePresenter
import de.taz.app.android.persistence.repository.FeedRepository
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.bookmarks.BookmarksFragment
import de.taz.app.android.ui.settings.SettingsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomePresenter(
    private val apiService: ApiService = ApiService.getInstance(),
    private val feedRepository: FeedRepository = FeedRepository.getInstance(),
    private val issueRepository: IssueRepository = IssueRepository.getInstance()
) : BasePresenter<HomeFragment, HomeDataController>(
    HomeDataController::class.java), HomeContract.Presenter {

    override suspend fun onRefresh() {
        withContext(Dispatchers.IO) {
            try {
                feedRepository.save(apiService.getFeeds())
                issueRepository.saveIfDoNotExist(apiService.getLastIssues())
            } catch (e: ApiService.ApiServiceException.NoInternetException) {
                getView()?.getMainView()?.showToast(R.string.toast_no_internet)
            }
        }
    }

    override fun onBottomNavigationItemClicked(menuItem: MenuItem, activated: Boolean) {
        when (menuItem.itemId) {
            R.id.bottom_navigation_action_bookmark -> getView()?.getMainView()?.showMainFragment(BookmarksFragment())
            R.id.bottom_navigation_action_settings -> getView()?.getMainView()?.showMainFragment(SettingsFragment())
        }
    }
}