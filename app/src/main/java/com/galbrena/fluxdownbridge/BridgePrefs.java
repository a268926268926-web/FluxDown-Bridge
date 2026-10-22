package com.galbrena.fluxdownbridge;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;
import java.util.Base64;

final class BridgePrefs {
    static final String PREFS = "fluxdown_bridge";
    static final String TOKEN = "token";
    static final String ENABLED = "enabled";
    static final String QUIET_NOTIFICATIONS = "quiet_notifications";

    private BridgePrefs() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String token(Context context) {
        SharedPreferences p = prefs(context);
        String token = p.getString(TOKEN, "");
        if (token != null && !token.isEmpty()) return token;
        token = newToken();
        p.edit().putString(TOKEN, token).apply();
        return token;
    }

    static String regenerateToken(Context context) {
        String token = newToken();
        prefs(context).edit().putString(TOKEN, token).apply();
        return token;
    }

    static boolean enabled(Context context) {
        return prefs(context).getBoolean(ENABLED, false);
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(ENABLED, enabled).apply();
    }

    static boolean quietNotifications(Context context) {
        return prefs(context).getBoolean(QUIET_NOTIFICATIONS, false);
    }

    static void setQuietNotifications(Context context, boolean quiet) {
        prefs(context).edit().putBoolean(QUIET_NOTIFICATIONS, quiet).apply();
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
