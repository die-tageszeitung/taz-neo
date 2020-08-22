package de.taz.app.android.ui.webview.pager

import android.app.Application
import androidx.lifecycle.*
import de.taz.app.android.api.interfaces.IssueOperations
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.SectionStub

class IssueContentViewModel(application: Application) : AndroidViewModel(application) {

    var articleList: List<ArticleStub> = emptyList()
    var sectionList: List<SectionStub> = emptyList()

    var sectionNameListLiveData = MutableLiveData<List<String?>>(emptyList())
    var issueOperationsLiveData = MutableLiveData<IssueOperations?>(null)
}