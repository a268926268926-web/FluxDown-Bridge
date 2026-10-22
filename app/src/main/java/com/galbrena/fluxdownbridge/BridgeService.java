package com.galbrena.fluxdownbridge;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public final class BridgeService extends Service {
    private static final String TAG = "FluxDownBridge";
    static final String ACTION_START = "com.galbrena.fluxdownbridge.START";
    static final String ACTION_STOP = "com.galbrena.fluxdownbridge.STOP";
    static final String ACTION_REFRESH = "com.galbrena.fluxdownbridge.REFRESH";
    private static final String CHANNEL_STANDARD = "fluxdown_bridge";
    private static final String CHANNEL_QUIET = "fluxdown_bridge_quiet";
    private static final int NOTIFICATION_ID = 4107;
    private BridgeServer server;

    static void start(Context context) {
        Intent intent = new Intent(context, BridgeService.class).setAction(ACTION_START);
        context.startForegroundService(intent);
    }

    static void stop(Context context) {
        BridgePrefs.setEnabled(context, false);
        context.startService(new Intent(context, BridgeService.class).setAction(ACTION_STOP));
    }

    static void refreshNotification(Context context) {
        if (!BridgePrefs.enabled(context)) return;
        context.startService(new Intent(context, BridgeService.class).setAction(ACTION_REFRESH));
    }

    static String notificationChannelId(Context context) {
        return BridgePrefs.quietNotifications(context) ? CHANNEL_QUIET : CHANNEL_STANDARD;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        Notification notification = notification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_REFRESH.equals(action) && server != null) {
            refreshForeground();
            return START_STICKY;
        }
        BridgePrefs.setEnabled(this, true);
        if (server == null) {
            server = new BridgeServer(this, BridgePrefs.token(this));
            try {
                server.start();
            } catch (Exception e) {
                server = null;
                BridgePrefs.setEnabled(this, false);
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        refreshForeground();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) server.stop();
        server = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel standard = new NotificationChannel(
                CHANNEL_STANDARD,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        standard.setDescription(getString(R.string.notification_text));
        manager.createNotificationChannel(standard);

        NotificationChannel quiet = new NotificationChannel(
                CHANNEL_QUIET,
                getString(R.string.notification_channel_quiet),
                NotificationManager.IMPORTANCE_MIN);
        quiet.setDescription(getString(R.string.notification_text));
        manager.createNotificationChannel(quiet);
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(
                this,
                0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        int accent = monetAccent();
        Notification.Builder builder = new Notification.Builder(this, notificationChannelId(this))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setColor(accent)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE);
        return builder.build();
    }

    private void refreshForeground() {
        logNotificationState();
        Notification updated = notification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, updated, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, updated);
        }
    }

    private void logNotificationState() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        boolean appEnabled = manager.areNotificationsEnabled();
        NotificationChannel channel = manager.getNotificationChannel(notificationChannelId(this));
        int importance = channel == null ? NotificationManager.IMPORTANCE_NONE : channel.getImportance();
        if (!appEnabled || importance == NotificationManager.IMPORTANCE_NONE) {
            Log.w(TAG, "foreground notification blocked"
                    + " appEnabled=" + appEnabled
                    + " channelImportance=" + importance);
        } else {
            Log.i(TAG, "foreground notification enabled"
                    + " appEnabled=true"
                    + " channelImportance=" + importance);
        }
    }

    private int monetAccent() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                return getColor(android.R.color.system_accent1_500);
            } catch (Exception ignored) {
                // OEMs occasionally omit the dynamic system palette.
            }
        }
        return Color.rgb(36, 107, 253);
    }

}
