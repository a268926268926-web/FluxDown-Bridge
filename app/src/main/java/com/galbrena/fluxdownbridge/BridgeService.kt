package com.galbrena.fluxdownbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log

class BridgeService : Service() {
    companion object {
        private const val TAG = "FluxDownBridge"
        const val ACTION_START = "com.galbrena.fluxdownbridge.START"
        const val ACTION_STOP = "com.galbrena.fluxdownbridge.STOP"
        const val ACTION_REFRESH = "com.galbrena.fluxdownbridge.REFRESH"
        private const val CHANNEL_STANDARD = "fluxdown_bridge"
        private const val CHANNEL_QUIET = "fluxdown_bridge_quiet"
        private const val NOTIFICATION_ID = 4107

        fun start(context: Context) {
            val intent = Intent(context, BridgeService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            BridgePrefs.setEnabled(context, false)
            context.startService(
                Intent(context, BridgeService::class.java).setAction(ACTION_STOP),
            )
        }

        fun refreshNotification(context: Context) {
            if (!BridgePrefs.enabled(context)) return
            context.startService(
                Intent(context, BridgeService::class.java).setAction(ACTION_REFRESH),
            )
        }

        fun notificationChannelId(context: Context): String =
            if (BridgePrefs.quietNotifications(context)) CHANNEL_QUIET else CHANNEL_STANDARD
    }

    private var server: BridgeServer? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForegroundCompat(buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_REFRESH -> if (server != null) {
                refreshForeground()
                return START_STICKY
            }
        }

        BridgePrefs.setEnabled(this, true)
        if (server == null) {
            val candidate = BridgeServer(BridgePrefs.token(this))
            try {
                candidate.start()
                server = candidate
            } catch (error: Exception) {
                Log.w(TAG, "bridge server failed to start: ${error.javaClass.simpleName}")
                candidate.stop()
                BridgePrefs.setEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        refreshForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val standard = NotificationChannel(
            CHANNEL_STANDARD,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_text)
        }
        manager.createNotificationChannel(standard)

        val quiet = NotificationChannel(
            CHANNEL_QUIET,
            getString(R.string.notification_channel_quiet),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.notification_text)
        }
        manager.createNotificationChannel(quiet)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, notificationChannelId(this))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setColor(monetAccent())
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun refreshForeground() {
        logNotificationState()
        startForegroundCompat(buildNotification())
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun logNotificationState() {
        val manager = getSystemService(NotificationManager::class.java)
        val appEnabled = manager.areNotificationsEnabled()
        val importance = manager
            .getNotificationChannel(notificationChannelId(this))
            ?.importance
            ?: NotificationManager.IMPORTANCE_NONE
        if (!appEnabled || importance == NotificationManager.IMPORTANCE_NONE) {
            Log.w(
                TAG,
                "foreground notification blocked appEnabled=$appEnabled channelImportance=$importance",
            )
        } else {
            Log.i(
                TAG,
                "foreground notification enabled appEnabled=true channelImportance=$importance",
            )
        }
    }

    private fun monetAccent(): Int {
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching { return getColor(android.R.color.system_accent1_500) }
        }
        return Color.rgb(36, 107, 253)
    }
}
