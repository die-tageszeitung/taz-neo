package de.taz.app.android.ui.home.page.coverflow

import android.os.Build
import android.view.View
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate

class CoverFlowAccessibilityDelegate(
    recyclerView: RecyclerView,
    private val accessibilityPaneTitle: CharSequence,
) : RecyclerViewAccessibilityDelegate(
    recyclerView
) {
            override fun onInitializeAccessibilityNodeInfo(
                host: View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    host.accessibilityPaneTitle = accessibilityPaneTitle
                }
                host.requestFocus()
                info.setCollectionInfo(null)
            }
        }