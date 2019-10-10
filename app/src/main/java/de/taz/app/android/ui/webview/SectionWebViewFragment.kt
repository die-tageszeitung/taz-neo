package de.taz.app.android.ui.webview

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import de.taz.app.android.R
import de.taz.app.android.api.models.Section
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class SectionWebViewFragment(val section: Section) : WebViewFragment(), AppWebViewCallback {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_webview_section, container, false)
    }

    override fun onResume() {
        CoroutineScope(Dispatchers.IO).launch {
            val file = File(
                ContextCompat.getExternalFilesDirs(
                    requireActivity().applicationContext,
                    null
                ).first(),
                "${section.issueBase.tag}/${section.sectionFileName}"
            )
            lifecycleScope.launch { fileLiveData.value = file }
            view?.let {
                it.findViewById<TextView>(R.id.section).apply{
                    text = section.title
                }
            }
        }
        super.onResume()
    }
}
