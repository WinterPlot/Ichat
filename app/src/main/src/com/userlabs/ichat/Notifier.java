package com.userlabs.ichat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import java.util.ArrayList;

/** Builds the message notifications: avatar, unread stack, inline reply, mark-read and copy-code. */
public class Notifier {

    public static final String CHANNEL = "ichat_messages_v2";
    public static final String CHANNEL_STATUS = "ichat_status";
    public static final String ACTION_REPLY = "com.userlabs.ichat.action.REPLY";
    public static final String ACTION_MARK_READ = "com.userlabs.ichat.action.MARK_READ";
    public static final String ACTION_COPY_CODE = "com.userlabs.ichat.action.COPY_CODE";
    public static final String KEY_REPLY = "reply_text";

    private static void ensureChannels(Context c, NotificationManager nm) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                c.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH);
            ch.enableLights(true);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
            NotificationChannel st = new NotificationChannel(CHANNEL_STATUS,
                c.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(st);
        }
    }

    private static int mutableFlag() {
        return Build.VERSION.SDK_INT >= 31 ? (1 << 25) : 0;
    }

    private static Uri dataFor(String address) {
        return new Uri.Builder().scheme("ichat").authority("n")
            .appendQueryParameter("a", address).build();
    }

    private static ArrayList<String> unreadLines(Context c, String address) {
        ArrayList<String> out = new ArrayList<String>();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(Telephony.Sms.Inbox.CONTENT_URI,
                new String[] { "body" }, "address = ? AND read = 0", new String[] { address },
                "date DESC LIMIT 5");
            while (cur != null && cur.moveToNext()) {
                String b = cur.getString(0);
                if (b != null) {
                    out.add(0, b);
                }
            }
        } catch (Exception e) {
            // provider unavailable: fall back to the single incoming body
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        return out;
    }

    private static Bitmap avatarBitmap(Context c, String address) {
        try {
            Contacts.Info info = Contacts.lookup(c, address);
            AvatarDrawable ad = Contacts.avatar(address, info);
            int size = Ui.dp(c, 64);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            ad.setBounds(0, 0, size, size);
            ad.draw(cv);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    public static void notifyIncoming(Context c, String address, String body) {
        if (Prefs.isMuted(c, address) || Prefs.isBlocked(c, address)) {
            return;
        }
        NotificationManager nm =
            (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        ensureChannels(c, nm);
        boolean hide = Prefs.hideNotifContent(c);
        String name = SmsUtil.contactName(c, address);

        Intent open = new Intent(c, ThreadActivity.class);
        open.putExtra("address", address);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, SmsUtil.notifId(address), open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(c);
        if (Build.VERSION.SDK_INT >= 26) {
            b.setChannelId(CHANNEL);
        }
        b.setSmallIcon(R.drawable.ic_stat_chat);
        b.setColor(Prefs.accent(c, address) | 0xFF000000);
        b.setCategory(Notification.CATEGORY_MESSAGE);
        b.setContentTitle(name);
        b.setContentIntent(pi);
        b.setAutoCancel(true);
        b.setDefaults(Notification.DEFAULT_ALL);
        b.setPriority(Notification.PRIORITY_HIGH);
        b.setWhen(System.currentTimeMillis());
        Bitmap large = avatarBitmap(c, address);
        if (large != null) {
            b.setLargeIcon(large);
        }

        if (hide) {
            b.setContentText(c.getString(R.string.new_message));
        } else {
            ArrayList<String> lines = unreadLines(c, address);
            if (lines.size() == 0) {
                lines.add(body);
            }
            if (lines.size() > 1) {
                Notification.InboxStyle st = new Notification.InboxStyle();
                for (int i = 0; i < lines.size(); i++) {
                    st.addLine(lines.get(i));
                }
                st.setSummaryText(c.getString(R.string.n_new_messages, lines.size()));
                b.setStyle(st);
                b.setContentText(lines.get(lines.size() - 1));
                b.setNumber(lines.size());
            } else {
                b.setContentText(body);
                b.setStyle(new Notification.BigTextStyle().bigText(body));
            }
        }

        Uri data = dataFor(address);

        if (Build.VERSION.SDK_INT >= 24) {
            RemoteInput ri = new RemoteInput.Builder(KEY_REPLY)
                .setLabel(c.getString(R.string.reply)).build();
            Intent ri_i = new Intent(ACTION_REPLY, data, c, ReplyReceiver.class);
            PendingIntent rpi = PendingIntent.getBroadcast(c, 1, ri_i,
                PendingIntent.FLAG_UPDATE_CURRENT | mutableFlag());
            Notification.Action reply = new Notification.Action.Builder(R.drawable.ic_send,
                c.getString(R.string.reply), rpi).addRemoteInput(ri).build();
            b.addAction(reply);
        }

        Intent mr = new Intent(ACTION_MARK_READ, data, c, ReplyReceiver.class);
        PendingIntent mpi = PendingIntent.getBroadcast(c, 2, mr, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        b.addAction(new Notification.Action.Builder(R.drawable.ic_done_all,
            c.getString(R.string.mark_read), mpi).build());

        String code = hide ? null : SmsUtil.otp(body);
        if (code != null) {
            Intent cc = new Intent(ACTION_COPY_CODE, data, c, ReplyReceiver.class);
            cc.putExtra("code", code);
            PendingIntent cpi = PendingIntent.getBroadcast(c, 3, cc, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            b.addAction(new Notification.Action.Builder(R.drawable.ic_copy,
                c.getString(R.string.copy_code, code), cpi).build());
        }

        nm.notify(SmsUtil.notifId(address), b.build());
    }

    /** Quiet one-off notification (scheduled-send result, MMS notice). */
    public static void notifySimple(Context c, int id, String title, String text, String openAddress) {
        NotificationManager nm =
            (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            return;
        }
        ensureChannels(c, nm);
        Intent open;
        if (openAddress != null) {
            open = new Intent(c, ThreadActivity.class);
            open.putExtra("address", openAddress);
        } else {
            open = new Intent(c, MainActivity.class);
        }
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c);
        if (Build.VERSION.SDK_INT >= 26) {
            b.setChannelId(CHANNEL_STATUS);
        }
        b.setSmallIcon(R.drawable.ic_stat_chat);
        b.setColor(Prefs.accent(c, null) | 0xFF000000);
        b.setContentTitle(title);
        b.setContentText(text);
        b.setStyle(new Notification.BigTextStyle().bigText(text));
        b.setContentIntent(pi);
        b.setAutoCancel(true);
        b.setPriority(Notification.PRIORITY_LOW);
        nm.notify(id, b.build());
    }

    public static void cancel(Context c, String address) {
        NotificationManager nm =
            (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(SmsUtil.notifId(address));
        }
    }
}
