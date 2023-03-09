package de.taz.app.android.ui.drawer.sectionList

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import de.taz.app.android.api.models.Article
import de.taz.app.android.monkey.getApplicationScope
import de.taz.app.android.persistence.repository.ArticleRepository
import de.taz.app.android.persistence.repository.BookmarkRepository
import kotlinx.coroutines.launch

class SectionDrawerViewModel(
    application: Application
) : AndroidViewModel(application) {

    val drawerOpen = MutableLiveData(false)

}