package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.*
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SectionPagerViewModel : ViewModel() {

    private val sectionKeyLiveData = MutableLiveData<String?>(null)
    var sectionKey
        get() = sectionKeyLiveData.value
        set(value) {
            sectionKeyLiveData.value = value
        }

    private val issueFeedNameLiveData = MutableLiveData<String?>(null)
    var issueFeedName
        get() = issueFeedNameLiveData.value
        set(value) {
            issueFeedNameLiveData.value = value
        }

    private val issueDateLiveData = MutableLiveData<String?>(null)
    var issueDate
        get() = issueDateLiveData.value
        set(value) {
            issueDateLiveData.value = value
        }


    private val issueStatusLiveData = MutableLiveData<IssueStatus?>(null)
    var issueStatus
        get() = issueStatusLiveData.value
        set(value) {
            issueStatusLiveData.value = value
        }


    val currentPositionLiveData = MutableLiveData(0)
    var currentPosition
        get() = currentPositionLiveData.value
        set(value) {
            currentPositionLiveData.value = value
        }

    val sectionStubListLiveData: LiveData<List<SectionStub>> = MediatorLiveData<List<SectionStub>>().apply {
            addSource(sectionKeyLiveData) { getIssueBySection(this) }
            addSource(issueDateLiveData) { getIssue(this) }
            addSource(issueFeedNameLiveData) { getIssue(this) }
            addSource(issueStatusLiveData) { getIssue(this) }
        }
    val issue = sectionStubListLiveData.value

    private fun getIssue(mediatorLiveData: MediatorLiveData<List<SectionStub>>) {
        runIfNotNull(
            issueDate,
            issueFeedName,
            issueStatus
        ) { issueDate, issueFeedName, issueStatus ->
            CoroutineScope(Dispatchers.IO).launch {
                mediatorLiveData.postValue(SectionRepository.getInstance().getSectionStubsForIssue(
                    issueFeedName, issueDate, issueStatus
                ))
            }
        }
    }

    private fun getIssueBySection(mediatorLiveData: MediatorLiveData<List<SectionStub>>) {
        CoroutineScope(Dispatchers.IO).launch {
            sectionKey?.let { sectionKey ->
                val sections = SectionRepository.getInstance().getAllSectionStubsForSectionName(sectionKey)
                currentPositionLiveData.postValue(sections.indexOfFirst { it.sectionFileName == sectionKey })
                mediatorLiveData.postValue(sections)
            }
        }
    }

}