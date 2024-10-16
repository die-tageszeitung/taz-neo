package de.taz.app.android.ui.webview.pager

import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.R
import de.taz.app.android.api.interfaces.ArticleOperations
import de.taz.app.android.api.models.Article
import de.taz.app.android.api.models.ArticleStub
import de.taz.app.android.api.models.SearchHit
import de.taz.app.android.ui.share.ShareArticleBottomSheet
import de.taz.app.android.util.BottomNavigationBehavior
import de.taz.app.android.util.getBottomNavigationBehavior
import de.taz.app.android.util.setBottomNavigationBehavior

class ArticleBottomActionBarNavigationHelper(
    private val onClickHandler: (MenuItem) -> Unit
) {
    private var bottomNavigationView: BottomNavigationView? = null
    // The view the [BottomNavigationBehavior] is attached to.
    // Could be the same as the bottomNavigation but for some reasons android requires a container
    // around it to remain compatible with ViewPagers
    private var behaviorView: View? = null


    private var isFixed = false
    private var isFixedForever = false
    private var defaultBehavior: BottomNavigationBehavior<View>? = null

    // The behavior is set on a container. See fragment_webview_pager.xml
    fun setBottomNavigationFromContainer(containerView: ViewGroup) {
        val bottomNavigationView = containerView.getChildAt(0) as? BottomNavigationView
        requireNotNull(bottomNavigationView)
        initializeBottomNavigation(bottomNavigationView)
        initializeBehaviorView(containerView)

        this.bottomNavigationView = bottomNavigationView
        this.behaviorView = containerView
    }

    fun onDestroyView() {
        bottomNavigationView?.apply {
            setOnItemSelectedListener(null)
        }
        bottomNavigationView = null
        behaviorView = null
        defaultBehavior = null
    }

    private fun initializeBehaviorView(view: View) {
        val behavior = view.getBottomNavigationBehavior()
        behavior?.initialize(view)
    }

    private fun initializeBottomNavigation(bottomNavigationView: BottomNavigationView) {
        bottomNavigationView.apply {
            menu.clear()
            inflateMenu(R.menu.navigation_bottom_article)

            setOnItemSelectedListener {
                onClickHandler(it)
                false
            }
        }
    }

    private fun setIcon(itemId: Int, @DrawableRes iconRes: Int) {
        bottomNavigationView?.apply {
            // prevent call while layouting
            post {
                menu.findItem(itemId)?.setIcon(iconRes)
            }
        }
    }

    private fun setVisibility(itemId: Int, isVisible: Boolean) {
        bottomNavigationView?.menu?.findItem(itemId)?.isVisible = isVisible
    }

    fun setShareIconVisibility(searchHit: SearchHit) {
        setVisibility(
            R.id.bottom_navigation_action_share,
            shouldShareIconBeVisible(searchHit)
        )
    }

    fun setShareIconVisibility(articleStub: ArticleOperations) {
        setVisibility(
            R.id.bottom_navigation_action_share,
            shouldShareIconBeVisible(articleStub)
        )
    }

    fun setArticleAudioVisibility(isVisible: Boolean) {
        setVisibility(
            R.id.bottom_navigation_action_audio,
            isVisible
        )
    }

    fun setArticleAudioMenuIcon(isPlaying: Boolean) {
        val icon = if (isPlaying) {
            R.drawable.ic_audio_filled
        } else {
            R.drawable.ic_audio
        }
        setIcon(R.id.bottom_navigation_action_audio, icon)
    }

    fun setBookmarkIcon(isBookmarked: Boolean) {
        val icon = if (isBookmarked) {
            R.drawable.ic_bookmark_filled
        } else {
            R.drawable.ic_bookmark
        }
        setIcon(R.id.bottom_navigation_action_bookmark, icon)
    }

    fun expand(animate: Boolean) {
        val view = behaviorView
        val behavior = view?.getBottomNavigationBehavior()
        if (view != null && behavior != null) {
            behavior.expand(view, animate)
        }
    }

    fun collapse(animate: Boolean) {
        val view = behaviorView
        val behavior = view?.getBottomNavigationBehavior()
        if (view != null && behavior != null) {
            behavior.collapse(view, animate)
        }
    }

    fun fixToolbar() {
        expand(animate = false)
        if (!isFixed && !isFixedForever) {
            defaultBehavior = behaviorView?.getBottomNavigationBehavior()
            behaviorView?.setBehavior(null)
            isFixed = true
        }
    }

    fun releaseToolbar() {
        if (isFixed && !isFixedForever) {
            behaviorView?.setBottomNavigationBehavior(defaultBehavior)
            isFixed = false
        }
    }

    fun fixToolbarForever() {
        fixToolbar()
        isFixedForever = true
    }

    private fun View.setBehavior(behavior: BottomNavigationBehavior<View>?) {
        val coordinatorLayoutParams = layoutParams as? CoordinatorLayout.LayoutParams
        if (coordinatorLayoutParams != null) {
            coordinatorLayoutParams.behavior = behavior
            layoutParams = coordinatorLayoutParams
        }
    }

    companion object {
        /**
         * Determine the share icon visibility.
         * The sharing icon will always be shown for public articles to urge users to subscribe.
         */
        fun shouldShareIconBeVisible(searchHit: SearchHit): Boolean {
            val isPublicArticle = searchHit.articleFileName.endsWith("public.html")
            return isPublicArticle || ShareArticleBottomSheet.isShareable(searchHit)
        }

        /**
         * Determine the share icon visibility.
         * The sharing icon will always be shown for public articles to urge users to subscribe.
         */
        fun shouldShareIconBeVisible(article: ArticleOperations): Boolean {
            val isPublicArticle = article.key.endsWith("public.html")
            return isPublicArticle || ShareArticleBottomSheet.isShareable(article)
        }
    }
}