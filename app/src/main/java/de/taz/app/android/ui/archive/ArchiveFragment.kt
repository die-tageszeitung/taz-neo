package de.taz.app.android.ui.archive

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.taz.app.android.R
import de.taz.app.android.base.BaseFragment

class ArchiveFragment : BaseFragment(), ArchiveContract.View {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_archive, container, false)
    }

}