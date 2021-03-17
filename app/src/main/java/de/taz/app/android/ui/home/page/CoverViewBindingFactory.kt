package de.taz.app.android.ui.home.page

import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.RequestManager
import de.taz.app.android.persistence.repository.IssuePublication
import de.taz.app.android.singletons.DateFormat
import de.taz.app.android.ui.cover.CoverView

interface CoverViewBindingFactory<T : CoverViewBinding<out CoverView>> {
    fun create(
        lifecycleOwner: LifecycleOwner,
        issuePublication: IssuePublication,
        dateFormat: DateFormat,
        glideRequestManager: RequestManager,
        onMomentViewActionListener: CoverViewActionListener
    ): T
}

class FrontpageViewBindingFactory : CoverViewBindingFactory<FrontpageViewBinding> {
    override fun create(
        lifecycleOwner: LifecycleOwner,
        issuePublication: IssuePublication,
        dateFormat: DateFormat,
        glideRequestManager: RequestManager,
        onMomentViewActionListener: CoverViewActionListener
    ): FrontpageViewBinding {
        return FrontpageViewBinding(
            lifecycleOwner,
            issuePublication,
            dateFormat,
            glideRequestManager,
            onMomentViewActionListener
        )
    }
}

class MomentViewBindingFactory : CoverViewBindingFactory<MomentViewBinding> {
    override fun create(
        lifecycleOwner: LifecycleOwner,
        issuePublication: IssuePublication,
        dateFormat: DateFormat,
        glideRequestManager: RequestManager,
        onMomentViewActionListener: CoverViewActionListener
    ): MomentViewBinding {
        return MomentViewBinding(
            lifecycleOwner,
            issuePublication,
            dateFormat,
            glideRequestManager,
            onMomentViewActionListener
        )
    }
}