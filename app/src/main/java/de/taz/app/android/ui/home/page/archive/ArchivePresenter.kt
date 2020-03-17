package de.taz.app.android.ui.home.page.archive

import de.taz.app.android.api.ApiService
import de.taz.app.android.download.DownloadService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.home.page.HomePagePresenter
import de.taz.app.android.singletons.DateHelper

class ArchivePresenter(
    apiService: ApiService = ApiService.getInstance(),
    downloadService: DownloadService = DownloadService.getInstance(),
    issueRepository: IssueRepository = IssueRepository.getInstance(),
    dateHelper: DateHelper = DateHelper.getInstance()
) : HomePagePresenter<ArchiveContract.View>(
    apiService = apiService,
    dateHelper = dateHelper,
    downloadService = downloadService,
    issueRepository = issueRepository
), ArchiveContract.Presenter

