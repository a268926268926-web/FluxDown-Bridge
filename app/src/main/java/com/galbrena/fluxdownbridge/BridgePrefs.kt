package com.galbrena.fluxdownbridge

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.security.SecureRandom
import java.util.Base64

internal object BridgePrefs {
    const val PREFS = "fluxdown_bridge"
    const val TOKEN = "token"
    const val ENABLED = "enabled"
    const val QUIET_NOTIFICATIONS = "quiet_notifications"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun token(context: Context): String {
        val preferences = prefs(context)
        preferences.getString(TOKEN, "").orEmpty().takeIf { it.isNotEmpty() }?.let { return it }
        return newToken().also {
            preferences.edit { putString(TOKEN, it) }
        }
    }

    fun regenerateToken(context: Context): String =
        newToken().also { token ->
            prefs(context).edit { putString(TOKEN, token) }
        }

    fun enabled(context: Context): Boolean =
        prefs(context).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(ENABLED, enabled) }
    }

    fun quietNotifications(context: Context): Boolean =
        prefs(context).getBoolean(QUIET_NOTIFICATIONS, false)

    fun setQuietNotifications(context: Context, quiet: Boolean) {
        prefs(context).edit { putBoolean(QUIET_NOTIFICATIONS, quiet) }
    }

    private fun newToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
