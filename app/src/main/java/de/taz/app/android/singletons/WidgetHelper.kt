package de.taz.app.android.singletons

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import de.taz.app.android.widget.MomentWidget

/**
 * Helper to trigger updates for the app widgets.
 */
object WidgetHelper {

    /**
     * Triggers an update for all active [MomentWidget] instances if any exist.
     *
     * Sending the [AppWidgetManager.ACTION_APPWIDGET_UPDATE] broadcast with the current
     * widget IDs ensures that the [MomentWidget.onUpdate] method is triggered.
     */
    fun updateMomentWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MomentWidget::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)

        // Only send the broadcast if there are active widgets to update
        if (ids.isNotEmpty()) {
            val intent = Intent(context, MomentWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
