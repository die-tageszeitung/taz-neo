package de.taz.app.android.ui.home.page.coverflow

import de.taz.app.android.api.ApiService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.DateHelper
import de.taz.app.android.ui.home.page.HomePagePresenter

class CoverflowPresenter(
    apiService: ApiService = ApiService.getInstance(),
    dateHelper: DateHelper = DateHelper.getInstance(),
    downloadService: DownloadService = DownloadService.getInstance(),
    issueRepository: IssueRepository = IssueRepository.getInstance()
) :
    HomePagePresenter<CoverflowFragment>(
        apiService = apiService,
        dateHelper = dateHelper,
        downloadService = downloadService,
        issueRepository = issueRepository
    ),
    CoverflowContract.Presenter
