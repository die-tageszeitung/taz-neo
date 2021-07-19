package de.taz.app.android.ui.drawer

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.fragment.app.FragmentContainerView
import com.google.android.material.navigation.NavigationView
import de.taz.app.android.R

class NavigationView @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null, defStyleAttr: Int = 0
) : NavigationView(context, attributeSet, defStyleAttr) {

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        val contentView = findViewById<FragmentContainerView>(R.id.drawer_menu_fragment_placeholder)
        contentView?.width?.let {
            layoutParams?.width = it
        }
    }

    /**
     * never make NavigationView invisible as we always want to show the logo above the content
     */
    override fun setVisibility(visibility: Int) {
        super.setVisibility(View.VISIBLE)
    }
}