package de.taz.app.android.ui.webview.pager

import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import de.taz.app.android.R
import de.taz.app.android.util.BottomNavigationBehavior

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
        val behavior = view.getBehavior()
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

    /**
     * Set the share icon visibility: Hence the article is public or the [onlineLink] is not null
     * @param onlineLink String holding the link to be shared
     * @param articleKey String holding the key of the article (or for search hit the filename)
     */
    fun setShareIconVisibility(onlineLink: String?, articleKey: String?) {
        setVisibility(
            R.id.bottom_navigation_action_share,
            shouldShareIconBeVisible(onlineLink, articleKey)
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
        val behavior = view?.getBehavior()
        if (view != null && behavior != null) {
            behavior.expand(view, animate)
        }
    }

    fun fixToolbar() {
        expand(animate = false)
        if (!isFixed && !isFixedForever) {
            defaultBehavior = behaviorView?.getBehavior()
            behaviorView?.setBehavior(null)
            isFixed = true
        }
    }

    fun releaseToolbar() {
        if (isFixed && !isFixedForever) {
            behaviorView?.setBehavior(defaultBehavior)
            isFixed = false
        }
    }

    fun fixToolbarForever() {
        fixToolbar()
        isFixedForever = true
    }

    private fun View.getBehavior(): BottomNavigationBehavior<View>? {
        val coordinatorLayoutParams = layoutParams as? CoordinatorLayout.LayoutParams
        return coordinatorLayoutParams?.behavior as? BottomNavigationBehavior
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
         * Determine the share icon visibility: Hence the article is public or the [onlineLink] is not null
         * @param onlineLink String holding the link to be shared
         * @param articleKey String holding the key of the article (or for search hit the filename)
         * @return true if the share icon should be shown
         */
        fun shouldShareIconBeVisible(onlineLink: String?, articleKey: String?): Boolean {
            return articleKey != null && articleKey.endsWith("public.html") || onlineLink != null
        }
    }
}