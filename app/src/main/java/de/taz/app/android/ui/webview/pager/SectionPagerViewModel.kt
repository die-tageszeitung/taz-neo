package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.*
import de.taz.app.android.api.models.IssueStatus
import de.taz.app.android.api.models.SectionStub
import de.taz.app.android.persistence.repository.SectionRepository
import de.taz.app.android.util.runIfNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        set(value) = issueFeedNameLiveData.postValue(value)

    private val issueDateLiveData = MutableLiveData<String?>(null)
    var issueDate
        get() = issueDateLiveData.value
        set(value) = issueDateLiveData.postValue(value)


    private val issueStatusLiveData = MutableLiveData<IssueStatus?>(null)
    var issueStatus
        get() = issueStatusLiveData.value
        set(value) = issueStatusLiveData.postValue(value)


    val currentPositionLiveData = MutableLiveData(0)
    val currentPosition
        get() = currentPositionLiveData.value

    val sectionStubListLiveData: LiveData<List<SectionStub>> =
        MediatorLiveData<List<SectionStub>>().apply {
            addSource(sectionKeyLiveData) { getSectionListBySection(this) }
            addSource(issueDateLiveData) { getSectionListByIssue(this) }
            addSource(issueFeedNameLiveData) { getSectionListByIssue(this) }
            addSource(issueStatusLiveData) { getSectionListByIssue(this) }
        }
    val sectionStubList = sectionStubListLiveData.value

    private fun getSectionListByIssue(mediatorLiveData: MediatorLiveData<List<SectionStub>>) {
        runIfNotNull(
            issueDate,
            issueFeedName,
            issueStatus
        ) { issueDate, issueFeedName, issueStatus ->
            CoroutineScope(Dispatchers.IO).launch {
                mediatorLiveData.postValue(
                    SectionRepository.getInstance().getSectionStubsForIssue(
                        issueFeedName, issueDate, issueStatus
                    )
                )
            }
        }
    }

    private fun getSectionListBySection(mediatorLiveData: MediatorLiveData<List<SectionStub>>) {
        CoroutineScope(Dispatchers.IO).launch {
            sectionKey?.let { sectionKey ->
                val sections = sectionStubList ?: SectionRepository.getInstance()
                    .getAllSectionStubsForSectionName(sectionKey)
                val index = sections.indexOfFirst { it.sectionFileName == sectionKey }
                if (index > 0) {
                    currentPositionLiveData.postValue(index)
                }
                mediatorLiveData.postValue(sections)
            }
        }
    }

}