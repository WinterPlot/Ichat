package com.userlabs.ichat;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Handles "quick reply" (reject call with message). Required for default SMS apps. */
public class RespondService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getData() != null) {
            String to = SmsUtil.addressFromUri(intent.getData());
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (to != null && to.length() > 0 && text != null && text.length() > 0) {
                try {
                    SmsUtil.send(this, to, text);
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }
}
