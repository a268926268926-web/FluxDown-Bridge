package com.galbrena.fluxdownbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.net.BindException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BridgeService : Service() {
    companion object {
        private const val TAG = "FluxDownBridge"
        const val ACTION_START = "com.galbrena.fluxdownbridge.START"
        const val ACTION_STOP = "com.galbrena.fluxdownbridge.STOP"
        const val ACTION_REFRESH = "com.galbrena.fluxdownbridge.REFRESH"
        private const val CHANNEL_STANDARD = "fluxdown_bridge"
        private const val CHANNEL_QUIET = "fluxdown_bridge_quiet"
        private const val NOTIFICATION_ID = 4107
        private val mutableRuntimeState = MutableStateFlow<BridgeRuntimeState>(BridgeRuntimeState.Stopped)
        internal val runtimeState = mutableRuntimeState.asStateFlow()

        fun start(context: Context) {
            BridgePrefs.setEnabled(context, true)
            if (mutableRuntimeState.value != BridgeRuntimeState.Running) {
                mutableRuntimeState.value = BridgeRuntimeState.Starting
            }
            val intent = Intent(context, BridgeService::class.java).setAction(ACTION_START)
            try {
                context.startForegroundService(intent)
            } catch (error: Exception) {
                Log.w(TAG, "foreground service failed to start: ${error.javaClass.simpleName}")
                mutableRuntimeState.value = BridgeRuntimeState.Failed("无法启动服务，请重新打开应用后重试。")
            }
        }

        fun stop(context: Context) {
            BridgePrefs.setEnabled(context, false)
            if (!context.stopService(Intent(context, BridgeService::class.java))) {
                mutableRuntimeState.value = BridgeRuntimeState.Stopped
            }
        }

        fun refreshNotification(context: Context) {
            if (mutableRuntimeState.value != BridgeRuntimeState.Running) return
            context.startService(
                Intent(context, BridgeService::class.java).setAction(ACTION_REFRESH),
            )
        }

        fun notificationChannelId(context: Context): String =
            if (BridgePrefs.quietNotifications(context)) CHANNEL_QUIET else CHANNEL_STANDARD

        fun regenerateToken(context: Context): String = BridgePrefs.regenerateToken(context)
    }

    private var server: BridgeServer? = null
    private var foregroundFailed = false
    private val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == BridgePrefs.TOKEN) server?.updateToken(BridgePrefs.token(this))
    }

    override fun onCreate() {
        super.onCreate()
        BridgePrefs.prefs(this).registerOnSharedPreferenceChangeListener(preferencesListener)
        if (!BridgePrefs.enabled(this)) {
            stopSelf()
            return
        }
        mutableRuntimeState.value = BridgeRuntimeState.Starting
        createChannels()
        refreshForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_STOP -> {
                BridgePrefs.setEnabled(this, false)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_REFRESH -> {
                if (server != null && BridgePrefs.enabled(this) && refreshForeground()) {
                    return START_STICKY
                }
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!BridgePrefs.enabled(this) || foregroundFailed) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (server == null) {
            mutableRuntimeState.value = BridgeRuntimeState.Starting
            val candidate = BridgeServer(BridgePrefs.token(this))
            try {
                candidate.start()
                server = candidate
                mutableRuntimeState.value = BridgeRuntimeState.Running
            } catch (error: Exception) {
                Log.w(TAG, "bridge server failed to start: ${error.javaClass.simpleName}")
                candidate.stop()
                mutableRuntimeState.value = BridgeRuntimeState.Failed(
                    if (error is BindException) {
                        "端口 ${BridgeServer.PORT} 已被占用，请停止占用端口的服务后重试。"
                    } else {
                        "无法启动本地监听（${error.javaClass.simpleName}），请重试。"
                    },
                )
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return if (refreshForeground()) START_STICKY else START_NOT_STICKY
    }

    override fun onDestroy() {
        BridgePrefs.prefs(this).unregisterOnSharedPreferenceChangeListener(preferencesListener)
        server?.stop()
        server = null
        if (mutableRuntimeState.value !is BridgeRuntimeState.Failed || !BridgePrefs.enabled(this)) {
            mutableRuntimeState.value = BridgeRuntimeState.Stopped
        }
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
            .setContentText(
                if (mutableRuntimeState.value == BridgeRuntimeState.Starting) {
                    "正在启动本地桥接服务"
                } else {
                    getString(R.string.notification_text)
                },
            )
            .setColor(monetAccent())
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    private fun refreshForeground(): Boolean {
        return try {
            logNotificationState()
            startForegroundCompat(buildNotification())
            true
        } catch (error: Exception) {
            Log.w(TAG, "foreground notification failed: ${error.javaClass.simpleName}")
            foregroundFailed = true
            mutableRuntimeState.value = BridgeRuntimeState.Failed("无法启动前台服务，请检查系统设置后重试。")
            stopSelf()
            false
        }
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
