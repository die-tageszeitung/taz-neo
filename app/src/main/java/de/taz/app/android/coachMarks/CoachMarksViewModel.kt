package de.taz.app.android.coachMarks

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CoachMarksViewModel : ViewModel() {

    private val _coachMarksFlow = MutableStateFlow<List<BaseCoachMark>>(emptyList())
    val coachmarksFlow = _coachMarksFlow.asStateFlow()

    fun setCoachMarks(coachMarks: List<BaseCoachMark>) {
        _coachMarksFlow.value = coachMarks
    }
}