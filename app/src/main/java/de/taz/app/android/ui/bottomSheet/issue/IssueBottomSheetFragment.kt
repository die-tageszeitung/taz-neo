package de.taz.app.android.ui.bottomSheet.issue

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.R
import de.taz.app.android.api.ApiService
import de.taz.app.android.api.models.IssueStub
import de.taz.app.android.persistence.repository.IssueRepository
import de.taz.app.android.singletons.FileHelper
import de.taz.app.android.ui.main.MainActivity
import de.taz.app.android.util.Log
import kotlinx.android.synthetic.main.fragment_bottom_sheet_issue.*
import kotlinx.android.synthetic.main.include_loading_screen.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class IssueBottomSheetFragment : BottomSheetDialogFragment() {

    private val log by Log
    private var afterDelete: Boolean = false
    private var issueStub: IssueStub? = null
    private var weakActivityReference: WeakReference<MainActivity>? = null

    companion object {
        fun create(
            mainActivity: MainActivity,
            issueStub: IssueStub
        ): IssueBottomSheetFragment {
            val fragment = IssueBottomSheetFragment()
            fragment.weakActivityReference = WeakReference(mainActivity)
            fragment.issueStub = issueStub
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return layoutInflater.inflate(R.layout.fragment_bottom_sheet_issue, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_bottom_sheet_issue_read?.setOnClickListener {
            issueStub?.let { issueStub ->
                weakActivityReference?.get()?.showIssue(issueStub)
            }
            dismiss()
        }

        fragment_bottom_sheet_issue_share?.setOnClickListener {
            issueStub?.let { issueStub ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val issue = IssueRepository.getInstance().getIssue(issueStub)
                    issue.moment.getAllFiles().last().let { image ->
                        val imageAsFile = FileHelper.getInstance().getFile(image)
                        val applicationId = view.context.packageName
                        val imageUriNew = FileProvider.getUriForFile(
                            view.context,
                            "${applicationId}.contentProvider",
                            imageAsFile
                        )

                        log.debug("imageUriNew: $imageUriNew")
                        log.debug("imageAsFile: $imageAsFile")
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, imageUriNew)
                            type = "image/jpg"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        view.context.startActivity(shareIntent)
                    }
                }
            }
            dismiss()
        }

        fragment_bottom_sheet_issue_delete?.setOnClickListener {
            issueStub?.let { issueStub ->
                afterDelete = true
                loading_screen?.visibility = View.VISIBLE

                // remove onclicklistener when clicking to greyed out area
                dialog?.window?.decorView?.findViewById<View>(
                    com.google.android.material.R.id.touch_outside
                )?.setOnClickListener(null)

                // do not allow to slide fragment away
                dialog?.window?.decorView?.findViewById<View>(
                    com.google.android.material.R.id.design_bottom_sheet
                )?.let { bottomSheetView ->
                    BottomSheetBehavior.from(bottomSheetView).isHideable = false
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val issueRepository = IssueRepository.getInstance()
                    issueRepository.getIssue(issueStub).delete()
                    ApiService.getInstance().getIssueByFeedAndDate(
                        issueStub.feedName, issueStub.date
                    )?.let { issueRepository.save(it) }
                    withContext(Dispatchers.Main) {
                        dismiss()
                    }
                }
            }
        }
    }

}