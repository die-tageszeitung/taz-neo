package de.taz.app.android.singletons

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import de.taz.app.android.widget.MomentWidget

/**
 * Singleton to handle calls to widget
 */
object WidgetHelper {

    /**
     * Update widgets if there are any active.
     * Found on [SO](https://stackoverflow.com/a/7738687)
     */
    fun updateWidget(context: Context) {
        val intent = Intent(context, MomentWidget::class.java)
        intent.setAction(ACTION_APPWIDGET_UPDATE)
        // Use an array and EXTRA_APPWIDGET_IDS instead of AppWidgetManager.EXTRA_APPWIDGET_ID,
        // since it seems the onUpdate() is only fired on that:
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(
            ComponentName(context, MomentWidget::class.java)
        )
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }
}