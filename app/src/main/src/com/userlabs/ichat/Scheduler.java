package com.userlabs.ichat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** "Send later": messages kept in preferences and fired by exact alarms (re-armed after reboot). */
public class Scheduler {

    public static final String ACTION_FIRE = "com.userlabs.ichat.SCHEDULED_SEND";

    public static class Item {
        public long id;
        public String address;
        public String body;
        public long when;
        public int subId = -1;
    }

    private static ArrayList<Item> load(Context c) {
        ArrayList<Item> out = new ArrayList<Item>();
        try {
            JSONArray arr = new JSONArray(Prefs.getString(c, "scheduled", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.id = o.getLong("id");
                it.address = o.getString("a");
                it.body = o.getString("b");
                it.when = o.getLong("t");
                it.subId = o.optInt("s", -1);
                out.add(it);
            }
        } catch (Exception e) {
            // corrupt store: start fresh
        }
        return out;
    }

    private static void save(Context c, ArrayList<Item> items) {
        try {
            JSONArray arr = new JSONArray();
            for (int i = 0; i < items.size(); i++) {
                Item it = items.get(i);
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("a", it.address);
                o.put("b", it.body);
                o.put("t", it.when);
                o.put("s", it.subId);
                arr.put(o);
            }
            Prefs.putString(c, "scheduled", arr.toString());
        } catch (Exception e) {
            // ignore
        }
    }

    public static ArrayList<Item> forAddress(Context c, String address) {
        ArrayList<Item> out = new ArrayList<Item>();
        ArrayList<Item> all = load(c);
        String k = Prefs.key(address);
        for (int i = 0; i < all.size(); i++) {
            if (Prefs.key(all.get(i).address).equals(k)) {
                out.add(all.get(i));
            }
        }
        java.util.Collections.sort(out, new java.util.Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.when < b.when ? -1 : (a.when > b.when ? 1 : 0);
            }
        });
        return out;
    }

    public static int count(Context c) {
        return load(c).size();
    }

    private static PendingIntent pending(Context c, long id) {
        Intent i = new Intent(ACTION_FIRE, Uri.parse("ichat://sched/" + id), c, ScheduledReceiver.class);
        return PendingIntent.getBroadcast(c, (int) (id % 1000000007L), i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void arm(Context c, Item it) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        PendingIntent pi = pending(c, it.id);
        if (Build.VERSION.SDK_INT >= 23) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, it.when, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, it.when, pi);
        }
    }

    public static Item add(Context c, String address, String body, long when, int subId) {
        ArrayList<Item> items = load(c);
        Item it = new Item();
        it.id = Math.max(System.currentTimeMillis(), items.isEmpty() ? 1L : items.get(items.size() - 1).id + 1L);
        it.address = address;
        it.body = body;
        it.when = when;
        it.subId = subId;
        items.add(it);
        save(c, items);
        arm(c, it);
        return it;
    }

    public static void cancel(Context c, long id) {
        ArrayList<Item> items = load(c);
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).id == id) {
                items.remove(i);
            }
        }
        save(c, items);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(pending(c, id));
        }
    }

    public static void rearmAll(Context c) {
        ArrayList<Item> items = load(c);
        for (int i = 0; i < items.size(); i++) {
            arm(c, items.get(i));
        }
    }

    public static void fire(Context c, long id) {
        ArrayList<Item> items = load(c);
        Item found = null;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == id) {
                found = items.get(i);
            }
        }
        if (found == null) {
            return;
        }
        String name = SmsUtil.contactName(c, found.address);
        // Do not silently discard a scheduled message if the app lost default-SMS status.
        if (!c.getPackageName().equals(android.provider.Telephony.Sms.getDefaultSmsPackage(c))) {
            Notifier.notifySimple(c, (int) (id % 100000L) + 700000,
                c.getString(R.string.sched_failed_title), c.getString(R.string.sched_failed, name),
                found.address);
            return;
        }
        try {
            SmsUtil.send(c, found.address, found.body, found.subId);
            items.remove(found);
            save(c, items);
            Notifier.notifySimple(c, (int) (id % 100000L) + 700000,
                c.getString(R.string.sched_sent_title), c.getString(R.string.sched_sent, name),
                found.address);
        } catch (Exception e) {
            // Keep the message in storage so the user can retry instead of losing it.
            Notifier.notifySimple(c, (int) (id % 100000L) + 700000,
                c.getString(R.string.sched_failed_title), c.getString(R.string.sched_failed, name),
                found.address);
        }
    }
}
