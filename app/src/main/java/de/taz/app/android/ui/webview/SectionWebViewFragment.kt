package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.MainActivity
import de.taz.app.android.R
import de.taz.app.android.api.models.Section
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class SectionWebViewFragment(val section: Section? = null) : WebViewFragment(), AppWebViewCallback {

    override val menuId: Int = R.menu.navigation_bottom_section
    override val headerId: Int = R.layout.fragment_webview_header_section

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        section?.let {
            CoroutineScope(Dispatchers.IO).launch {
                val file = File(
                    ContextCompat.getExternalFilesDirs(
                        requireActivity().applicationContext,
                        null
                    ).first(),
                    "${section.issueBase.tag}/${section.sectionFileName}"
                )
                lifecycleScope.launch { fileLiveData.value = file }
            }
        }
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onBottomNavigationItemSelected(menuItem: MenuItem) {

    }


    override fun onSwipeLeft(e1: MotionEvent, e2: MotionEvent) {
        super.onSwipeLeft(e1, e2)
        CoroutineScope(Dispatchers.IO).launch {
            section?.nextSection()?.let {
                (activity as MainActivity).showSection(it, R.anim.slide_in_left, R.anim.slide_out_left)
            }
        }
    }

    override fun onSwipeRight(e1: MotionEvent, e2: MotionEvent) {
        super.onSwipeLeft(e1, e2)
        CoroutineScope(Dispatchers.IO).launch {
            section?.previousSection()?.let {
                (activity as MainActivity).showSection(it, R.anim.slide_in_right, R.anim.slide_out_right)
            }
        }
    }

}
