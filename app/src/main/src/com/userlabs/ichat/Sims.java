package com.userlabs.ichat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Active SIM cards (needs READ_PHONE_STATE, Android 5.1+). Everything degrades to single-SIM. */
public class Sims {

    public static class Sim {
        public int subId;
        public int slot; // 0-based
        public String name;
        public String number;
        public int color;

        /** "SIM 1" style label, 1-based. */
        public int displaySlot() {
            return slot + 1;
        }
    }

    private static final int[] SLOT_COLORS = { 0xFF0A84FF, 0xFFFF8A00, 0xFF30C06E, 0xFF9B51E0 };
    private static ArrayList<Sim> cache = new ArrayList<Sim>();
    private static long cacheTime = 0;

    public static boolean hasPermission(Context c) {
        if (Build.VERSION.SDK_INT < 22) {
            return false;
        }
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED;
    }

    public static void invalidate() {
        cacheTime = 0;
    }

    /** Active SIMs ordered by slot. Empty when unavailable. Cached for a few seconds. */
    public static synchronized ArrayList<Sim> list(Context c) {
        long now = System.currentTimeMillis();
        if (now - cacheTime < 8000L) {
            return cache;
        }
        ArrayList<Sim> out = new ArrayList<Sim>();
        if (hasPermission(c)) {
            try {
                SubscriptionManager sm = SubscriptionManager.from(c);
                List<SubscriptionInfo> infos = sm.getActiveSubscriptionInfoList();
                if (infos != null) {
                    for (int i = 0; i < infos.size(); i++) {
                        SubscriptionInfo si = infos.get(i);
                        Sim s = new Sim();
                        s.subId = si.getSubscriptionId();
                        s.slot = Math.max(0, si.getSimSlotIndex());
                        CharSequence dn = si.getDisplayName();
                        s.name = dn != null ? dn.toString() : "";
                        s.number = si.getNumber();
                        s.color = SLOT_COLORS[s.slot % SLOT_COLORS.length];
                        out.add(s);
                    }
                }
            } catch (Exception e) {
                // permission revoked or telephony unavailable: treat as no SIM info
            }
        }
        Collections.sort(out, new Comparator<Sim>() {
            @Override
            public int compare(Sim a, Sim b) {
                return a.slot - b.slot;
            }
        });
        cache = out;
        cacheTime = now;
        return out;
    }

    public static boolean isDual(Context c) {
        return list(c).size() > 1;
    }

    public static Sim byId(Context c, int subId) {
        if (subId < 0) {
            return null;
        }
        ArrayList<Sim> l = list(c);
        for (int i = 0; i < l.size(); i++) {
            if (l.get(i).subId == subId) {
                return l.get(i);
            }
        }
        return null;
    }

    /** The phone's default SMS SIM, or the first active one. -1 when unknown. */
    public static int defaultSubId(Context c) {
        ArrayList<Sim> l = list(c);
        if (l.size() == 0) {
            return -1;
        }
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                int d = SubscriptionManager.getDefaultSmsSubscriptionId();
                if (byId(c, d) != null) {
                    return d;
                }
            } catch (Exception e) {
                // fall through
            }
        }
        return l.get(0).subId;
    }

    /** SIM to use for a conversation: the remembered one if still present, else the default. */
    public static int preferred(Context c, String address) {
        if (!isDual(c)) {
            return -1;
        }
        if (Prefs.simRemember(c) && address != null) {
            int s = Prefs.simFor(c, address);
            if (byId(c, s) != null) {
                return s;
            }
        }
        return defaultSubId(c);
    }

    /** 1-based slot number for a subscription, or 0 when unknown or not dual-SIM. */
    public static int slotNumber(Context c, int subId) {
        if (subId < 0 || !isDual(c)) {
            return 0;
        }
        Sim s = byId(c, subId);
        return s == null ? 0 : s.displaySlot();
    }

    public static int colorOf(Context c, int subId) {
        Sim s = byId(c, subId);
        return s == null ? 0xFF888888 : s.color;
    }

    public static String label(Context c, Sim s) {
        String n = s.name != null ? s.name.trim() : "";
        if (n.length() == 0) {
            return c.getString(R.string.sim_n, s.displaySlot());
        }
        return c.getString(R.string.sim_pill, s.displaySlot(), n);
    }
}
