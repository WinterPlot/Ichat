package com.userlabs.ichat;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;

/** The resolved colour palette for the current theme mode, accent and (optionally) one chat. */
public class Look {

    public static final int[] ACCENTS = {
        0xFF0A84FF, // blue
        0xFF5E5CE6, // indigo
        0xFF6750A4, // violet (brand)
        0xFFFF2D87, // pink
        0xFFFF453A, // red
        0xFFFF8A00, // orange
        0xFFFFC400, // amber
        0xFF7ED321, // lime
        0xFF30C06E, // green
        0xFF00B3A4, // teal
        0xFF00B8E6, // cyan
        0xFF64748B  // slate
    };

    public boolean dark;
    public boolean amoled;
    public int accent;
    public int accent2;
    public int onAccent;
    public int bg;
    public int surface;
    public int surface2;
    public int text;
    public int text2;
    public int divider;
    public int inBubble;
    public int inText;
    public int danger;

    public static boolean isDark(Context c) {
        int m = Prefs.themeMode(c);
        if (m == Prefs.MODE_DARK || m == Prefs.MODE_AMOLED) {
            return true;
        }
        if (m == Prefs.MODE_LIGHT) {
            return false;
        }
        int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return night == Configuration.UI_MODE_NIGHT_YES;
    }

    public static Look get(Context c, String address) {
        Look k = new Look();
        k.dark = isDark(c);
        k.amoled = Prefs.themeMode(c) == Prefs.MODE_AMOLED && k.dark;
        k.accent = Prefs.accent(c, address) | 0xFF000000;
        k.accent2 = hueShift(k.accent, 34f);
        k.onAccent = lum(k.accent) > 0.62f ? 0xFF111111 : 0xFFFFFFFF;
        if (k.dark) {
            k.bg = k.amoled ? 0xFF000000 : 0xFF101116;
            k.surface = k.amoled ? 0xFF0B0B0D : 0xFF1A1B23;
            k.surface2 = k.amoled ? 0xFF161618 : 0xFF252631;
            k.text = 0xFFF1F2F7;
            k.text2 = 0xFF9A9CB0;
            k.divider = 0x1FFFFFFF;
            k.inBubble = k.amoled ? 0xFF1C1C1E : 0xFF262833;
            k.inText = 0xFFF1F2F7;
        } else {
            k.bg = 0xFFF7F8FC;
            k.surface = 0xFFFFFFFF;
            k.surface2 = 0xFFEEF0F6;
            k.text = 0xFF171827;
            k.text2 = 0xFF77798C;
            k.divider = 0x14171827;
            k.inBubble = 0xFFECEEF5;
            k.inText = 0xFF171827;
        }
        k.danger = 0xFFFF453A;
        return k;
    }

    /** Copy of this palette using another accent colour (used for live previews). */
    public Look withAccent(int a) {
        Look k = new Look();
        k.dark = dark;
        k.amoled = amoled;
        k.accent = a | 0xFF000000;
        k.accent2 = hueShift(k.accent, 34f);
        k.onAccent = lum(k.accent) > 0.62f ? 0xFF111111 : 0xFFFFFFFF;
        k.bg = bg;
        k.surface = surface;
        k.surface2 = surface2;
        k.text = text;
        k.text2 = text2;
        k.divider = divider;
        k.inBubble = inBubble;
        k.inText = inText;
        k.danger = danger;
        return k;
    }

    /** Picks the matching base theme. Call before setContentView. */
    public static void theme(Activity a) {
        a.setTheme(isDark(a) ? R.style.AppDark : R.style.AppLight);
    }

    /** Colours the status/navigation bars and window background to match. */
    public static void window(Activity a, Look k, int statusColor) {
        Window w = a.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(k.bg));
        w.setStatusBarColor(statusColor);
        w.setNavigationBarColor(k.bg);
        if (Build.VERSION.SDK_INT >= 23) {
            View d = w.getDecorView();
            int f = d.getSystemUiVisibility();
            if (lum(statusColor) > 0.55f) {
                f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= 26) {
                if (lum(k.bg) > 0.55f) {
                    f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    f &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            d.setSystemUiVisibility(f);
        }
    }

    // ---------------------------------------------------------------- colour math

    public static int blend(int a, int b, float t) {
        float s = 1f - t;
        int ar = (int) (Color.alpha(a) * s + Color.alpha(b) * t);
        int r = (int) (Color.red(a) * s + Color.red(b) * t);
        int g = (int) (Color.green(a) * s + Color.green(b) * t);
        int bl = (int) (Color.blue(a) * s + Color.blue(b) * t);
        return Color.argb(ar, r, g, bl);
    }

    public static int alpha(int c, int a) {
        return (c & 0x00FFFFFF) | (a << 24);
    }

    public static float lum(int c) {
        return (0.299f * Color.red(c) + 0.587f * Color.green(c) + 0.114f * Color.blue(c)) / 255f;
    }

    public static int hueShift(int c, float deg) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[0] = (hsv[0] + deg) % 360f;
        hsv[1] = Math.min(1f, hsv[1] * 0.95f);
        hsv[2] = Math.min(1f, hsv[2] * 1.04f);
        return Color.HSVToColor(Color.alpha(c), hsv);
    }

    public static int darken(int c, float f) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[2] = hsv[2] * f;
        return Color.HSVToColor(Color.alpha(c), hsv);
    }

    public static int lighten(int c, float amount) {
        return blend(c, 0xFFFFFFFF, amount);
    }
}
