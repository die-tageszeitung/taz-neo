package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
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
}
