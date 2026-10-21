package com.userlabs.ichat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Required to qualify as a default SMS app. Pictures and group MMS are not decoded yet. */
public class MmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        // We cannot download or read the message, but the user should at least know it arrived.
        Notifier.notifySimple(ctx, 900001, ctx.getString(R.string.mms_title),
            ctx.getString(R.string.mms_body), null);
    }
}
