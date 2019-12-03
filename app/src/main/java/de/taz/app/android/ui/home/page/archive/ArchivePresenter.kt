package de.taz.app.android.ui.home.page.archive

import de.taz.app.android.api.ApiService
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.ui.home.page.HomePagePresenter

class ArchivePresenter(
    apiService: ApiService = ApiService.getInstance(),
    issueRepository: IssueRepository = IssueRepository.getInstance()
) : HomePagePresenter<ArchiveContract.View>(
    apiService, issueRepository
), ArchiveContract.Presenter

