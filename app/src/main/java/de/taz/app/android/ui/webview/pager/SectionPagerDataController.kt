package de.taz.app.android.ui.webview.pager

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import de.taz.app.android.api.models.Section
import de.taz.app.android.base.BaseDataController
import de.taz.app.android.persistence.repository.IssueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class SectionPagerDataController: BaseDataController(), SectionPagerContract.DataController {
    override var currentPosition: Int = 0
    private val sectionList = MutableLiveData<List<Section>>(emptyList())

    override fun setInitialSection(section: Section) {
        setSectionListAndPosition(listOf(section), 0)

        viewModelScope.launch(Dispatchers.IO) {
            // FIXME: maybe use a single call on some repo
            val issueRepository = IssueRepository.getInstance()
            val issueStub = issueRepository.getIssueStubForSection(section.sectionFileName)
            val issue = issueRepository.getIssue(issueStub)
            val sections = issue.sectionList
            setSectionListAndPosition(sections, sections.indexOf(section))
        }
    }

    private fun setSectionListAndPosition(sections: List<Section>, position: Int) {
        currentPosition = if (position > 0) position else 0
        sectionList.postValue(sections)
    }

    override fun getSectionList(): LiveData<List<Section>> = sectionList

    override fun trySetSection(section: Section): Boolean {
        val position = sectionList.value?.indexOf(section) ?: -1
        if (position >= 0) {
            currentPosition = position
            return true
        }
        return false
    }
}