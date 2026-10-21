package com.userlabs.ichat;

import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.Telephony;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SMS plumbing: sending with status tracking, formatting, smart replies and code detection. */
public class SmsUtil {

    public static final int TYPE_INBOX = 1;
    public static final int TYPE_SENT = 2;
    public static final int TYPE_OUTBOX = 4;
    public static final int TYPE_FAILED = 5;

    private static final Pattern OTP_DASHED = Pattern.compile("(?<!\\d)(\\d{3})[- ](\\d{3})(?!\\d)");
    private static final Pattern OTP_PLAIN = Pattern.compile("(?<![\\d.])(\\d{4,8})(?![\\d])");

    public static boolean isDefault(Context c) {
        return c.getPackageName().equals(Telephony.Sms.getDefaultSmsPackage(c));
    }

    /** Returns the contact name, or the number itself if unknown. Blocking: use off the UI thread. */
    public static String contactName(Context c, String number) {
        if (number == null || number.length() == 0) {
            return "";
        }
        return Contacts.lookup(c, number).name;
    }

    // ---------------------------------------------------------------- time formatting

    public static String clock(Context c, long when) {
        String p = Prefs.time24(c) ? "HH:mm" : "h:mm a";
        return new SimpleDateFormat(p, Locale.getDefault()).format(new Date(when));
    }

    private static int dayDiff(long when) {
        Calendar a = Calendar.getInstance();
        a.set(Calendar.HOUR_OF_DAY, 0);
        a.set(Calendar.MINUTE, 0);
        a.set(Calendar.SECOND, 0);
        a.set(Calendar.MILLISECOND, 0);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(when);
        b.set(Calendar.HOUR_OF_DAY, 0);
        b.set(Calendar.MINUTE, 0);
        b.set(Calendar.SECOND, 0);
        b.set(Calendar.MILLISECOND, 0);
        long diff = a.getTimeInMillis() - b.getTimeInMillis();
        return (int) Math.round(diff / 86400000.0);
    }

    private static boolean sameYear(long when) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(when);
        return now.get(Calendar.YEAR) == t.get(Calendar.YEAR);
    }

    /** Short stamp for the conversation list. */
    public static String formatTime(Context c, long when) {
        int dd = dayDiff(when);
        if (dd == 0) {
            return clock(c, when);
        }
        if (dd == 1) {
            return c.getString(R.string.yesterday);
        }
        if (dd > 1 && dd < 7) {
            return new SimpleDateFormat("EEE", Locale.getDefault()).format(new Date(when));
        }
        String p = sameYear(when) ? "d MMM" : "d MMM yy";
        return new SimpleDateFormat(p, Locale.getDefault()).format(new Date(when));
    }

    /** Day separator label inside a conversation. */
    public static String dayLabel(Context c, long when) {
        int dd = dayDiff(when);
        if (dd == 0) {
            return c.getString(R.string.today);
        }
        if (dd == 1) {
            return c.getString(R.string.yesterday);
        }
        String p;
        if (dd > 1 && dd < 7) {
            p = "EEEE";
        } else if (sameYear(when)) {
            p = "EEE, d MMM";
        } else {
            p = "d MMM yyyy";
        }
        return new SimpleDateFormat(p, Locale.getDefault()).format(new Date(when));
    }

    public static String fullDate(Context c, long when) {
        String p = Prefs.time24(c) ? "EEE, d MMM yyyy HH:mm:ss" : "EEE, d MMM yyyy h:mm:ss a";
        return new SimpleDateFormat(p, Locale.getDefault()).format(new Date(when));
    }

    // ---------------------------------------------------------------- addresses

    /** Extracts the first number from sms:123?body=x style URIs. */
    public static String addressFromUri(Uri u) {
        if (u == null) return null;
        String a = u.getSchemeSpecificPart();
        if (a == null) return null;
        int q = a.indexOf('?');
        if (q >= 0) a = a.substring(0, q);
        a = Uri.decode(a).trim();
        // sms://123 is parsed as an authority by Android; accept both forms.
        if (a.startsWith("//")) a = a.substring(2);
        int sep = a.indexOf(',');
        if (sep < 0) sep = a.indexOf(';');
        if (sep >= 0) a = a.substring(0, sep).trim();
        return a.isEmpty() ? null : a;
    }

    public static int notifId(String address) {
        return address == null ? 0 : address.toLowerCase(java.util.Locale.ROOT).hashCode();
    }

    public static long threadIdFor(Context c, String address) {
        try {
            return Telephony.Threads.getOrCreateThreadId(c, address);
        } catch (Exception e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- sending

    /**
     * Sends the text. When iChat is the default app the message is stored first (as "sending")
     * and updated to sent / failed / delivered by {@link SentReceiver}. Returns the stored row, or
     * null when nothing could be stored. Throws if the radio refuses the message.
     */
    public static Uri send(Context c, String to, String text) {
        return send(c, to, text, -1);
    }

    /** Same as {@link #send(Context, String, String)} but through a specific SIM (-1 = system default). */
    public static Uri send(Context c, String to, String text, int subId) {
        SmsManager sm = SmsManager.getDefault();
        if (subId >= 0 && Build.VERSION.SDK_INT >= 22) {
            try {
                sm = SmsManager.getSmsManagerForSubscriptionId(subId);
            } catch (Exception e) {
                sm = SmsManager.getDefault();
                subId = -1;
            }
        }
        ArrayList<String> parts = sm.divideMessage(text);
        Uri stored = null;
        if (isDefault(c)) {
            ContentValues v = new ContentValues();
            v.put("address", to);
            v.put("body", text);
            v.put("date", System.currentTimeMillis());
            v.put("read", 1);
            v.put("seen", 1);
            if (subId >= 0) {
                v.put("sub_id", subId);
            }
            try {
                stored = c.getContentResolver().insert(Telephony.Sms.Outbox.CONTENT_URI, v);
            } catch (Exception e) {
                stored = null;
            }
            if (stored == null && subId >= 0) {
                // some ROMs have no sub_id column: store without it so tracking still works
                v.remove("sub_id");
                try {
                    stored = c.getContentResolver().insert(Telephony.Sms.Outbox.CONTENT_URI, v);
                } catch (Exception e) {
                    stored = null;
                }
            }
        }
        ArrayList<PendingIntent> sentList = null;
        ArrayList<PendingIntent> deliveredList = null;
        if (stored != null) {
            long id = 0;
            try {
                id = ContentUris.parseId(stored);
            } catch (Exception e) {
                id = System.currentTimeMillis();
            }
            sentList = new ArrayList<PendingIntent>();
            deliveredList = new ArrayList<PendingIntent>();
            for (int i = 0; i < parts.size(); i++) {
                int base = (int) ((id & 0xFFFFFL) * 256L) + i * 2;
                Intent si = new Intent(SentReceiver.ACTION_SENT, stored, c, SentReceiver.class);
                Intent di = new Intent(SentReceiver.ACTION_DELIVERED, stored, c, SentReceiver.class);
                sentList.add(PendingIntent.getBroadcast(c, base, si, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                deliveredList.add(PendingIntent.getBroadcast(c, base + 1, di,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
        }
        try {
            sm.sendMultipartTextMessage(to, null, parts, sentList, deliveredList);
        } catch (RuntimeException e) {
            if (stored != null) {
                setType(c, stored, TYPE_FAILED, null);
            }
            throw e;
        }
        return stored;
    }

    public static void setType(Context c, Uri row, int type, String onlyIfWhere) {
        try {
            ContentValues v = new ContentValues();
            v.put("type", type);
            c.getContentResolver().update(row, v, onlyIfWhere, null);
        } catch (Exception e) {
            // not the default app any more, or the row was deleted
        }
    }

    public static void setDeliveryStatus(Context c, Uri row, int status) {
        try {
            ContentValues v = new ContentValues();
            v.put("status", status);
            c.getContentResolver().update(row, v, null, null);
        } catch (Exception e) {
            // ignore
        }
    }

    /** Re-sends a failed message: the old failed row is replaced by a fresh one. */
    public static Uri resend(Context c, long oldId, String to, String text, int subId) {
        deleteMessage(c, oldId);
        return send(c, to, text, subId);
    }

    /**
     * Queries the SMS table with an extra trailing "sub_id" column when the device has one.
     * Callers check {@code cursor.getColumnCount() > base.length} before reading it.
     */
    public static Cursor querySms(Context c, String[] base, String sel, String[] args, String order) {
        if (Build.VERSION.SDK_INT >= 22) {
            String[] cols = new String[base.length + 1];
            System.arraycopy(base, 0, cols, 0, base.length);
            cols[base.length] = "sub_id";
            try {
                Cursor cur = c.getContentResolver().query(Telephony.Sms.CONTENT_URI, cols, sel, args, order);
                if (cur != null) {
                    return cur;
                }
            } catch (Exception e) {
                // no sub_id column: fall back below
            }
        }
        return c.getContentResolver().query(Telephony.Sms.CONTENT_URI, base, sel, args, order);
    }

    // ---------------------------------------------------------------- deleting / reading

    public static boolean deleteMessage(Context c, long id) {
        try {
            return c.getContentResolver().delete(
                ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id), null, null) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean deleteThread(Context c, String address) {
        long tid = threadIdFor(c, address);
        if (tid <= 0) {
            return false;
        }
        try {
            c.getContentResolver().delete(Telephony.Sms.CONTENT_URI, "thread_id = ?",
                new String[] { String.valueOf(tid) });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void markThreadRead(Context c, long threadId) {
        if (threadId <= 0 || !isDefault(c)) {
            return;
        }
        try {
            ContentValues v = new ContentValues();
            v.put("read", 1);
            v.put("seen", 1);
            c.getContentResolver().update(Telephony.Sms.CONTENT_URI, v,
                "thread_id = ? AND read = 0", new String[] { String.valueOf(threadId) });
        } catch (Exception e) {
            // ignore
        }
    }

    // ---------------------------------------------------------------- text intelligence

    /** Length info: [parts, used, remaining, encoding]. */
    public static int[] lengthInfo(String text) {
        try {
            return SmsMessage.calculateLength(text, false);
        } catch (Exception e) {
            return new int[] { 1, text.length(), 160 - text.length(), 1 };
        }
    }

    /** Finds a one-time code in a message, or returns null. */
    public static String otp(String body) {
        if (body == null || body.length() > 400) {
            return null;
        }
        String low = body.toLowerCase(Locale.ENGLISH);
        boolean hint = low.contains("code") || low.contains("otp") || low.contains("verif")
            || low.contains("pin") || low.contains("password") || low.contains("passcode")
            || low.contains("mot de passe") || low.contains("one-time") || low.contains("confirm")
            || low.contains("security") || low.contains("s\u00E9curit\u00E9") || low.contains("login");
        if (!hint) {
            return null;
        }
        Matcher m = OTP_DASHED.matcher(body);
        if (m.find()) {
            return m.group(1) + m.group(2);
        }
        m = OTP_PLAIN.matcher(body);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static boolean has(String low, String[] words) {
        for (int i = 0; i < words.length; i++) {
            if (low.contains(words[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean startsWithAny(String low, String[] words) {
        for (int i = 0; i < words.length; i++) {
            if (low.startsWith(words[i])) {
                return true;
            }
        }
        return false;
    }

    /** Offline smart replies for the last incoming message (English and French). */
    public static String[] suggestions(Context c, String last) {
        if (last == null || last.trim().length() == 0) {
            return new String[0];
        }
        if (otp(last) != null) {
            return new String[0];
        }
        String low = last.trim().toLowerCase(Locale.ENGLISH);
        final String wave = "\uD83D\uDC4B";
        final String smile = "\uD83D\uDE42";
        final String thumbs = "\uD83D\uDC4D";
        final String party = "\uD83C\uDF89";
        boolean fr = has(low, new String[] { "bonjour", "salut", "merci", "comment", "\u00E7a va",
            "ca va", "o\u00F9", "quand", "oui", "bonsoir", "s'il", "stp", "svp", "est-ce", "coucou",
            "tu ", "vous " });
        boolean question = low.indexOf('?') >= 0;

        if (has(low, new String[] { "thank", "thx", "merci" })) {
            return fr ? new String[] { "De rien !", "Avec plaisir " + smile, thumbs }
                : new String[] { "You're welcome!", "Anytime " + smile, thumbs };
        }
        if (has(low, new String[] { "happy birthday", "joyeux anniversaire", "bon anniversaire" })) {
            return fr ? new String[] { "Merci beaucoup ! " + party, "Merci " + smile }
                : new String[] { "Thank you so much! " + party, "Thanks! " + smile };
        }
        if (has(low, new String[] { "how are you", "how r u", "comment vas", "\u00E7a va", "ca va",
            "comment \u00E7a va", "tu vas bien" })) {
            return fr ? new String[] { "\u00C7a va et toi ?", "Tout va bien " + smile, "Tr\u00E8s bien, merci !" }
                : new String[] { "I'm good, you?", "All good " + smile, "Doing great, thanks!" };
        }
        if (startsWithAny(low, new String[] { "hi", "hello", "hey", "yo", "good morning",
            "good evening", "salut", "bonjour", "bonsoir", "coucou" })) {
            return fr ? new String[] { "Salut ! " + wave, "Bonjour, comment vas-tu ?", "Quoi de neuf ?" }
                : new String[] { "Hey! " + wave, "Hi, how are you?", "What's up?" };
        }
        if (has(low, new String[] { "call me", "appelle", "rappelle" })) {
            return fr ? new String[] { "Je t'appelle", "Donne-moi 5 minutes", "Je ne peux pas parler" }
                : new String[] { "Calling you now", "Give me 5 minutes", "I can't talk now" };
        }
        if (question && has(low, new String[] { "where", "o\u00F9" })) {
            return fr ? new String[] { "J'arrive", "\u00C0 la maison", "Je t'envoie ma position" }
                : new String[] { "On my way", "At home", "I'll share my location" };
        }
        if (question && has(low, new String[] { "when", "what time", "quand", "quelle heure" })) {
            return fr ? new String[] { "Demain", "Ce soir", "Je v\u00E9rifie" }
                : new String[] { "Tomorrow", "Tonight", "Let me check" };
        }
        if (question) {
            return fr ? new String[] { "Oui", "Non", "Je v\u00E9rifie" }
                : new String[] { "Yes", "No", "Let me check" };
        }
        if (has(low, new String[] { "see you", "\u00E0 plus", "a plus", "bye", "good night",
            "bonne nuit", "talk later" })) {
            return fr ? new String[] { "\u00C0 plus !", thumbs, "Bonne soir\u00E9e " + smile }
                : new String[] { "See you!", thumbs, "Talk later " + smile };
        }
        String[] q = Prefs.quickReplies(c);
        if (q.length > 4) {
            String[] cut = new String[4];
            System.arraycopy(q, 0, cut, 0, 4);
            return cut;
        }
        return q;
    }
}
