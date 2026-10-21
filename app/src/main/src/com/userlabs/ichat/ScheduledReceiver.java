package com.userlabs.ichat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires a scheduled message when its alarm goes off. */
public class ScheduledReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }
        try {
            long id = Long.parseLong(intent.getData().getLastPathSegment());
            Scheduler.fire(ctx, id);
        } catch (Exception e) {
            // malformed intent
        }
    }
}
