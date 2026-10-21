package com.userlabs.ichat;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Locale;

/** All user settings and small per-conversation state, kept in one SharedPreferences file. */
public class Prefs {

    public static final int MODE_AUTO = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;
    public static final int MODE_AMOLED = 3;

    public static final int DEFAULT_ACCENT = 0xFF0A84FF;

    private static SharedPreferences sp;
    /** Bumped on every appearance change so open screens know to rebuild themselves. */
    private static int rev = 0;

    private static synchronized SharedPreferences sp(Context c) {
        if (sp == null) {
            Context app = c.getApplicationContext();
            sp = (app != null ? app : c).getSharedPreferences("ichat_prefs", Context.MODE_PRIVATE);
        }
        return sp;
    }

    public static int rev() {
        return rev;
    }

    public static void touch() {
        rev++;
    }

    // ---------------------------------------------------------------- raw helpers

    public static int getInt(Context c, String k, int def) {
        try {
            return sp(c).getInt(k, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static void putInt(Context c, String k, int v) {
        sp(c).edit().putInt(k, v).apply();
    }

    public static boolean getBool(Context c, String k, boolean def) {
        try {
            return sp(c).getBoolean(k, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static void putBool(Context c, String k, boolean v) {
        sp(c).edit().putBoolean(k, v).apply();
    }

    public static String getString(Context c, String k, String def) {
        try {
            return sp(c).getString(k, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static void putString(Context c, String k, String v) {
        if (v == null) {
            sp(c).edit().remove(k).apply();
        } else {
            sp(c).edit().putString(k, v).apply();
        }
    }

    public static void remove(Context c, String k) {
        sp(c).edit().remove(k).apply();
    }

    public static boolean has(Context c, String k) {
        return sp(c).contains(k);
    }

    // ---------------------------------------------------------------- address key

    /** Normalises a phone number / sender id so +25779123456 and 079123456 share one key. */
    public static String key(String a) {
        if (a == null) {
            return "";
        }
        StringBuilder d = new StringBuilder();
        boolean alpha = false;
        for (int i = 0; i < a.length(); i++) {
            char ch = a.charAt(i);
            if (ch >= '0' && ch <= '9') {
                d.append(ch);
            } else if (Character.isLetter(ch)) {
                alpha = true;
            }
        }
        if (alpha || d.length() < 6) {
            return a.trim().toLowerCase(Locale.ENGLISH);
        }
        String s = d.toString();
        return s;
    }

    // ---------------------------------------------------------------- appearance

    public static int themeMode(Context c) {
        return getInt(c, "theme_mode", MODE_AUTO);
    }

    public static void setThemeMode(Context c, int m) {
        putInt(c, "theme_mode", m);
        touch();
    }

    /** Bubble style for a chat: the chat's own override, otherwise the global choice. */
    public static int bubbleStyle(Context c, String address) {
        if (address != null && has(c, "cs_" + key(address))) {
            return getInt(c, "cs_" + key(address), 0);
        }
        return getInt(c, "bubble_style", 0);
    }

    public static int accent(Context c, String address) {
        if (address != null && has(c, "cc_" + key(address))) {
            return getInt(c, "cc_" + key(address), DEFAULT_ACCENT);
        }
        return getInt(c, "accent", DEFAULT_ACCENT);
    }

    public static int wallpaper(Context c, String address) {
        if (address != null && has(c, "cw_" + key(address))) {
            return getInt(c, "cw_" + key(address), 0);
        }
        return getInt(c, "wallpaper", 0);
    }

    /** 0..100, 50 is the style's natural roundness. */
    public static int roundness(Context c, String address) {
        if (address != null && has(c, "cr_" + key(address))) {
            return getInt(c, "cr_" + key(address), 50);
        }
        return getInt(c, "roundness", 50);
    }

    public static int textSize(Context c) {
        return getInt(c, "text_size", 16);
    }

    public static void setTextSize(Context c, int sp) {
        putInt(c, "text_size", sp);
        touch();
    }

    public static boolean hasChatOverride(Context c, String address) {
        String k = key(address);
        return has(c, "cs_" + k) || has(c, "cc_" + k) || has(c, "cw_" + k) || has(c, "cr_" + k);
    }

    public static void clearChatOverride(Context c, String address) {
        String k = key(address);
        sp(c).edit().remove("cs_" + k).remove("cc_" + k).remove("cw_" + k).remove("cr_" + k).apply();
        touch();
    }

    /** Writes a style value either globally (address == null) or as a per-chat override. */
    public static void setStyleValue(Context c, String address, String what, int value) {
        // what: "s" style, "c" accent, "w" wallpaper, "r" roundness
        String k;
        if (address == null) {
            if ("s".equals(what)) {
                k = "bubble_style";
            } else if ("c".equals(what)) {
                k = "accent";
            } else if ("w".equals(what)) {
                k = "wallpaper";
            } else {
                k = "roundness";
            }
        } else {
            k = "c" + what + "_" + key(address);
        }
        putInt(c, k, value);
        touch();
    }

    public static boolean groupMessages(Context c) {
        return getBool(c, "group_msgs", true);
    }

    public static boolean showAvatars(Context c) {
        return getBool(c, "show_avatars", true);
    }

    public static boolean time24(Context c) {
        return getBool(c, "time24", true);
    }

    public static boolean timeAlways(Context c) {
        return getBool(c, "time_always", false);
    }

    public static boolean smartReplies(Context c) {
        return getBool(c, "smart_replies", true);
    }

    public static boolean hideNotifContent(Context c) {
        return getBool(c, "hide_notif", false);
    }

    public static boolean haptics(Context c) {
        return getBool(c, "haptics", true);
    }

    public static boolean simRemember(Context c) {
        return getBool(c, "sim_remember", true);
    }

    /** Subscription id last used with (or received from) this conversation; -1 when none. */
    public static int simFor(Context c, String address) {
        return getInt(c, "sim_" + key(address), -1);
    }

    public static void setSim(Context c, String address, int subId) {
        if (subId < 0) {
            remove(c, "sim_" + key(address));
        } else {
            putInt(c, "sim_" + key(address), subId);
        }
    }

    public static boolean enterSends(Context c) {
        return getBool(c, "enter_sends", false);
    }

    // ---------------------------------------------------------------- lists

    private static ArrayList<String> list(Context c, String k) {
        ArrayList<String> out = new ArrayList<String>();
        String s = getString(c, k, "");
        if (s != null && s.length() > 0) {
            String[] parts = s.split("\n");
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].length() > 0) {
                    out.add(parts[i]);
                }
            }
        }
        return out;
    }

    private static void saveList(Context c, String k, ArrayList<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(l.get(i));
        }
        putString(c, k, sb.toString());
    }

    private static boolean inList(Context c, String k, String address) {
        return list(c, k).contains(key(address));
    }

    private static void setInList(Context c, String k, String address, boolean on) {
        ArrayList<String> l = list(c, k);
        String kk = key(address);
        l.remove(kk);
        if (on) {
            l.add(kk);
        }
        saveList(c, k, l);
    }

    public static boolean isPinned(Context c, String a) {
        return inList(c, "pinned", a);
    }

    public static void setPinned(Context c, String a, boolean on) {
        setInList(c, "pinned", a, on);
    }

    public static boolean isMuted(Context c, String a) {
        return inList(c, "muted", a);
    }

    public static void setMuted(Context c, String a, boolean on) {
        setInList(c, "muted", a, on);
    }

    public static boolean isBlocked(Context c, String a) {
        return inList(c, "blocked", a);
    }

    public static void setBlocked(Context c, String a, boolean on) {
        setInList(c, "blocked", a, on);
    }

    public static ArrayList<String> blockedKeys(Context c) {
        return list(c, "blocked");
    }

    public static void unblockKey(Context c, String key) {
        ArrayList<String> l = list(c, "blocked");
        l.remove(key);
        saveList(c, "blocked", l);
    }

    /** Conversations the user manually marked as unread (local flag). */
    public static boolean isMarkedUnread(Context c, String a) {
        return inList(c, "manual_unread", a);
    }

    public static void setMarkedUnread(Context c, String a, boolean on) {
        setInList(c, "manual_unread", a, on);
    }

    // ---------------------------------------------------------------- drafts

    public static String draft(Context c, String address) {
        return getString(c, "d_" + key(address), "");
    }

    public static void setDraft(Context c, String address, String text) {
        if (text == null || text.trim().length() == 0) {
            remove(c, "d_" + key(address));
        } else {
            putString(c, "d_" + key(address), text);
        }
    }

    // ---------------------------------------------------------------- starred

    public static boolean isStarred(Context c, long id) {
        return list(c, "starred").contains(String.valueOf(id));
    }

    public static void setStarred(Context c, long id, boolean on) {
        ArrayList<String> l = list(c, "starred");
        String s = String.valueOf(id);
        l.remove(s);
        if (on) {
            l.add(s);
        }
        saveList(c, "starred", l);
    }

    public static ArrayList<Long> starredIds(Context c) {
        ArrayList<String> l = list(c, "starred");
        ArrayList<Long> out = new ArrayList<Long>();
        for (int i = 0; i < l.size(); i++) {
            try {
                out.add(Long.valueOf(Long.parseLong(l.get(i))));
            } catch (Exception e) {
                // skip corrupt entry
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- quick replies / emoji

    public static String[] quickReplies(Context c) {
        String s = getString(c, "quick_replies", null);
        if (s == null || s.trim().length() == 0) {
            return new String[] { "OK", "On my way", "Thanks!", "Call you later" };
        }
        ArrayList<String> l = new ArrayList<String>();
        String[] parts = s.split("\n");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].trim().length() > 0) {
                l.add(parts[i].trim());
            }
        }
        return l.toArray(new String[l.size()]);
    }

    public static ArrayList<String> recentEmoji(Context c) {
        return list(c, "recent_emoji");
    }

    public static void addRecentEmoji(Context c, String e) {
        ArrayList<String> l = list(c, "recent_emoji");
        l.remove(e);
        l.add(0, e);
        while (l.size() > 32) {
            l.remove(l.size() - 1);
        }
        saveList(c, "recent_emoji", l);
    }
}
