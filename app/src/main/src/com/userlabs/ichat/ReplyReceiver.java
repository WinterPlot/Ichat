package com.userlabs.ichat;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Handles the notification buttons: inline reply, mark as read, copy verification code. */
public class ReplyReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || intent.getData() == null) {
            return;
        }
        String address = intent.getData().getQueryParameter("a");
        String action = intent.getAction();
        if (address == null || action == null) {
            return;
        }
        if (Notifier.ACTION_REPLY.equals(action)) {
            Bundle results = RemoteInput.getResultsFromIntent(intent);
            CharSequence text = results != null ? results.getCharSequence(Notifier.KEY_REPLY) : null;
            if (text != null && text.toString().trim().length() > 0) {
                try {
                    SmsUtil.send(ctx, address, text.toString().trim(), Sims.preferred(ctx, address));
                } catch (Exception e) {
                    Toast.makeText(ctx, ctx.getString(R.string.could_not_send, e.getMessage()),
                        Toast.LENGTH_LONG).show();
                }
            }
            SmsUtil.markThreadRead(ctx, SmsUtil.threadIdFor(ctx, address));
            Notifier.cancel(ctx, address);
        } else if (Notifier.ACTION_MARK_READ.equals(action)) {
            SmsUtil.markThreadRead(ctx, SmsUtil.threadIdFor(ctx, address));
            Notifier.cancel(ctx, address);
        } else if (Notifier.ACTION_COPY_CODE.equals(action)) {
            String code = intent.getStringExtra("code");
            if (code != null) {
                Ui.copy(ctx, code);
                Toast.makeText(ctx, R.string.code_copied, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
