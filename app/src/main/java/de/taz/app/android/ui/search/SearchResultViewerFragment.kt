package de.taz.app.android.ui.search

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import de.taz.app.android.ui.TazViewerFragment
import de.taz.app.android.ui.webview.pager.BookmarkPagerFragment
import kotlin.reflect.KClass

class SearchResultViewerFragment(private val articleKey: String) : TazViewerFragment() {
    private val searchResultPagerViewModel: SearchResultPagerViewModel by activityViewModels()

    override val fragmentClass: KClass<out Fragment> = BookmarkPagerFragment::class

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            searchResultPagerViewModel.articleFileNameLiveData.postValue(articleKey)
        }
    }
}