package com.userlabs.ichat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms scheduled messages after a reboot or an app update. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Scheduler.rearmAll(ctx);
        }
    }
}
