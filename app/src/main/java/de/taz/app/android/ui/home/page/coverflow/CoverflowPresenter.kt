package de.taz.app.android.ui.home.page.coverflow

import de.taz.app.android.api.ApiService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.home.page.HomePagePresenter

class CoverflowPresenter(
    apiService: ApiService = ApiService.getInstance(),
    issueRepository: IssueRepository = IssueRepository.getInstance()
) :
    HomePagePresenter<CoverflowFragment>(
        apiService, issueRepository
    ),
    CoverflowContract.Presenter
