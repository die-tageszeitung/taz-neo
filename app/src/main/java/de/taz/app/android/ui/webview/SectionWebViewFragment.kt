package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.*
import androidx.core.content.ContextCompat
import de.taz.app.android.R
import de.taz.app.android.api.models.Section
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class SectionWebViewFragment(val section: Section) : WebViewFragment(), ArticleWebViewCallback {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_section_webview, container, false)
    }

    override fun onResume() {
        CoroutineScope(Dispatchers.IO).launch {
            file = File(
                ContextCompat.getExternalFilesDirs(
                    requireActivity().applicationContext,
                    null
                ).first(),
                "${section.issueBase.tag}/${section.sectionFileName}"
            )
        }
        super.onResume()
    }
}
