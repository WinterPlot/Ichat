package com.userlabs.ichat;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

/** Receives SMS_DELIVER (only delivered while iChat is the default SMS app). */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (msgs == null || msgs.length == 0 || msgs[0] == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < msgs.length; i++) {
            if (msgs[i] != null) {
                sb.append(msgs[i].getMessageBody());
            }
        }
        String address = msgs[0].getDisplayOriginatingAddress();
        String body = sb.toString();
        if (address == null) {
            return;
        }

        // Blocked senders are still stored (so nothing is lost) but arrive silently, already read.
        boolean blocked = Prefs.isBlocked(ctx, address);

        ContentValues v = new ContentValues();
        v.put("address", address);
        v.put("body", body);
        v.put("date", System.currentTimeMillis());
        v.put("date_sent", msgs[0].getTimestampMillis());
        v.put("read", blocked ? 1 : 0);
        v.put("seen", blocked ? 1 : 0);
        int subId = intent.getIntExtra("subscription", -1);
        if (subId < 0) {
            subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX", -1);
        }
        boolean stored = false;
        if (subId >= 0) {
            v.put("sub_id", subId);
            try {
                ctx.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, v);
                stored = true;
            } catch (Exception e) {
                v.remove("sub_id");
            }
            // reply from the SIM that received the message
            Prefs.setSim(ctx, address, subId);
        }
        if (!stored) {
            try {
                ctx.getContentResolver().insert(Telephony.Sms.Inbox.CONTENT_URI, v);
            } catch (Exception e) {
                // keep going so the user still gets the notification
            }
        }
        if (!blocked) {
            Notifier.notifyIncoming(ctx, address, body);
        }
    }
}
