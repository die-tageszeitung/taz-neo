package de.taz.app.android.singletons

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.taz.app.android.R
import de.taz.app.android.sentry.SentryWrapper
import de.taz.app.android.util.SingletonHolder


class NotificationHelper private constructor(private val applicationContext: Context) {

    private val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)

    companion object : SingletonHolder<NotificationHelper, Context>(::NotificationHelper)

    fun showNotification(
        @StringRes title: Int,
        @StringRes body: Int,
        channelId: String,
        bigText: Boolean = false,
        pendingIntent: PendingIntent? = null,
        notificationId: Int = 0
    ) {
        showNotification(
            applicationContext.getString(title),
            applicationContext.getString(body),
            channelId, bigText, pendingIntent, notificationId
        )
    }

    fun showNotification(
        title: String,
        body: String,
        channelId: String,
        bigText: Boolean = false,
        pendingIntent: PendingIntent? = null,
        notificationId: Int = 0
    ) {

        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(ContextCompat.getColor(applicationContext, R.color.colorAccent))
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (bigText) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
            )
        }

        pendingIntent?.let {
            builder.setContentIntent(pendingIntent)
            builder.setAutoCancel(true)
        }

        if (ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManagerCompat.notify(notificationId, builder.build())
        } else {
            SentryWrapper.captureMessage("Could not show notification. Permission not granted.")
        }
    }

}