package com.galbrena.fluxdownbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!BridgePrefs.enabled(context)) return;
        try {
            BridgeService.start(context);
        } catch (Exception ignored) {
            // Some vendor builds restrict boot-time foreground services. Opening the app retries.
        }
    }
}
