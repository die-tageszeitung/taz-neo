package de.taz.app.android.util

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.taz.app.android.R

class NotificationHelper private constructor(private val applicationContext: Context) {

    private val notificationManagerCompat = NotificationManagerCompat.from(applicationContext)

    companion object : SingletonHolder<NotificationHelper, Context>(::NotificationHelper)

    fun showNotification(
        title: String,
        content: String,
        channelId: String,
        bigText: Boolean = false,
        pendingIntent: PendingIntent? = null,
        notificationId: Int = 0
    ) {

        var builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        if (bigText) {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(content)
            )
        }

        pendingIntent?.let {
            builder.setContentIntent(pendingIntent)
            builder.setAutoCancel(true)
        }

        notificationManagerCompat.notify(notificationId, builder.build())


    }

}