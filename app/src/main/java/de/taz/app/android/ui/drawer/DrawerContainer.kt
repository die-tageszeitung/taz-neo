package de.taz.app.android.ui.drawer

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * This should be the second child view (after the content) of a DrawerLayout.
 *
 * Android proposes to use a NavigationView instead, but the NavigationView is intended to be used
 * with a menu structure defined in XML, while we want to have arbitrary code within the Drawer.
 * Furthermore the material implementation prevents us to draw the logo outside of the bounding
 * of the NavigationView due to the way it implements its shape helper for rounded borders.
 *
 */
class DrawerContainer @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null
) : FrameLayout(context, attributeSet) {

    override fun setVisibility(visibility: Int) {
        // Ignore

        // Never make DrawerContainer invisible as we always want to show the logo above the content.
        // By default the drawer container visibility will be set to gone when the drawer is hidden.

        // TODO (johannes): might be optimized to hide/show the actual drawer placeholder in response
    }
}