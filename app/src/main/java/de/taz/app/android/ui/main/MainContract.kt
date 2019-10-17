package de.taz.app.android.ui.main

import android.widget.ImageView
import androidx.annotation.AnimRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.Issue
import de.taz.app.android.api.models.Section

interface MainContract {
    interface View {

        fun getLifecycleOwner(): LifecycleOwner

        fun highlightDrawerIcon(imageView: ImageView)

        fun setDrawerTitle(@StringRes stringId: Int)

        fun showDrawerFragment(fragment: Fragment)

        fun showArticle(article: Article, @AnimRes enterAnimation: Int = 0, @AnimRes exitAnimation: Int = 0)

        fun showSection(section: Section, @AnimRes enterAnimation: Int = 0, @AnimRes exitAnimation: Int = 0)

        fun showMainFragment(fragment: Fragment, @AnimRes enterAnimation: Int = 0, @AnimRes exitAnimation: Int = 0)

        fun closeDrawer()

        fun showToast(@StringRes stringId: Int)

        fun showToast(string: String)

    }

    interface Presenter {
        fun onItemClicked(imageView: ImageView)
    }

    interface DataController {
        fun getIssue(): Issue?

        fun observeIssue(lifeCycleOwner: LifecycleOwner, newDataBlock: (Issue?) -> (Unit))

        fun observeIssueIsDownloaded(lifeCycleOwner: LifecycleOwner, newDataBlock: (Boolean) -> (Unit))
    }

}