package de.taz.app.android.ui.bottomSheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import de.taz.app.android.R

class AddBottomSheetDialog : BottomSheetDialogFragment() {
    var fragment: Fragment? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_bottom_sheet, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fragment?.let {
            childFragmentManager.beginTransaction().replace(R.id.dialog_bottom_sheet, it).commit()
        }
    }

    override fun onStart() {
        super.onStart()
        //this removes the translucent status of the status bar which causes some weird flickering
        dialog?.window?.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        
        //this forces the sheet to appear at max height even on landscape
        val behavior = BottomSheetBehavior.from(requireView().parent as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    companion object {
        fun newInstance(fragmentToShow: Fragment): AddBottomSheetDialog {
            val fragment = AddBottomSheetDialog()
            fragment.fragment = fragmentToShow
            return fragment
        }
    }
}