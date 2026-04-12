package app.hydra.manager

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import java.security.MessageDigest

object AppNotificationHelper {

    const val CHANNEL_UPDATES = "updates"
    private const val NOTIFICATION_ID_UPDATES = 1001
    private const val NOTIFICATION_ID_RELEASE_UPDATES = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_UPDATES,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    fun showBackendUpdateNotification(context: Context) {
        ensureChannels(context)
        if (!NotificationPreferences.areUpdateNotificationsEnabled(context)) return
        if (!canPostNotifications(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(context.getString(R.string.notification_title_updates_live))
            .setContentText(context.getString(R.string.notification_text_updates_live))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATES, notification)
    }

    @SuppressLint("MissingPermission")
    fun showBackendUpdateNotification(
        context: Context,
        backendName: String,
        backendUrl: String
    ) {
        ensureChannels(context)
        if (!NotificationPreferences.areUpdateNotificationsEnabled(context)) return
        if (!canPostNotifications(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resolvedName = backendName.trim().ifBlank {
            context.getString(R.string.backend_default_label)
        }
        val notificationId = backendNotificationId(backendUrl)
        val contentText = context.getString(R.string.notification_text_backend_update)
        val contentTitle = context.getString(
            R.string.notification_title_backend_update_format,
            resolvedName
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    @SuppressLint("MissingPermission")
    fun showReleaseUpdateNotification(
        context: Context,
        releaseLabel: String? = null,
        releaseUrl: String? = null
    ) {
        ensureChannels(context)
        if (!NotificationPreferences.areUpdateNotificationsEnabled(context)) return
        if (!canPostNotifications(context)) return

        val intent = if (!releaseUrl.isNullOrBlank()) {
            Intent(Intent.ACTION_VIEW, releaseUrl.toUri()).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = context.getString(R.string.notification_text_release_live)
        val contentTitle = releaseLabel?.trim().orEmpty().ifBlank {
            context.getString(R.string.notification_title_release_live)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_RELEASE_UPDATES, notification)
    }

    private fun backendNotificationId(backendUrl: String): Int {
        val digest = MessageDigest.getInstance("SHA-256").digest(backendUrl.trim().toByteArray())
        val base = digest.take(4).fold(0) { acc, byte ->
            (acc shl 8) or (byte.toInt() and 0xff)
        }
        val safe = (base and Int.MAX_VALUE) % 1_000_000
        return 2000 + safe
    }
}
