package de.taz.app.android.ui.drawer

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import de.taz.app.android.api.models.Issue

class SelectedIssueViewModel : ViewModel() {

    val selectedIssue = MutableLiveData<Issue?>().apply {
        postValue(null)
    }
}
