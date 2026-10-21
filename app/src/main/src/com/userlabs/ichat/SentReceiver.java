package com.userlabs.ichat;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.telephony.SmsMessage;

/** Receives the radio's "sent" and "delivered" results and records them on the stored message. */
public class SentReceiver extends BroadcastReceiver {

    public static final String ACTION_SENT = "com.userlabs.ichat.SMS_SENT";
    public static final String ACTION_DELIVERED = "com.userlabs.ichat.SMS_DELIVERED";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Uri row = intent.getData();
        if (row == null || intent.getAction() == null) {
            return;
        }
        if (ACTION_SENT.equals(intent.getAction())) {
            if (getResultCode() == Activity.RESULT_OK) {
                // only promote "sending" to "sent"; never overwrite a failure
                SmsUtil.setType(ctx, row, SmsUtil.TYPE_SENT, "type = " + SmsUtil.TYPE_OUTBOX);
            } else {
                SmsUtil.setType(ctx, row, SmsUtil.TYPE_FAILED, null);
            }
        } else if (ACTION_DELIVERED.equals(intent.getAction())) {
            int status = 0;
            byte[] pdu = intent.getByteArrayExtra("pdu");
            if (pdu != null) {
                try {
                    SmsMessage m = SmsMessage.createFromPdu(pdu);
                    if (m != null) {
                        int s = m.getStatus();
                        status = s < 32 ? 0 : (s < 64 ? 32 : 64);
                    }
                } catch (Exception e) {
                    status = 0;
                }
            }
            SmsUtil.setDeliveryStatus(ctx, row, status);
        }
    }
}
