package com.userlabs.ichat;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/** Small view/drawable helpers shared by every screen. */
public class Ui {

    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static float density(Context c) {
        return c.getResources().getDisplayMetrics().density;
    }

    public static GradientDrawable rect(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static GradientDrawable rectStroke(int fill, int stroke, float strokePx, float radiusPx) {
        GradientDrawable g = rect(fill, radiusPx);
        g.setStroke((int) strokePx, stroke);
        return g;
    }

    public static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** Circular, unbounded-looking touch ripple for icon buttons. */
    public static Drawable rippleOval(int rippleColor) {
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(Color.BLACK);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask);
    }

    /** Ripple over an existing background (or nothing) clipped to a rounded rect. */
    public static Drawable ripple(int rippleColor, Drawable content, float radiusPx) {
        GradientDrawable mask = rect(Color.BLACK, radiusPx);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    public static Drawable tinted(Context c, int res, int tint) {
        Drawable d = c.getDrawable(res);
        if (d == null) {
            return null;
        }
        d = d.mutate();
        d.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        return d;
    }

    public static void icon(ImageView v, int res, int tint) {
        v.setImageDrawable(tinted(v.getContext(), res, tint));
    }

    /** Styles a square icon button (ImageView) with ripple and tint. */
    public static void iconButton(ImageView v, int res, int tint, int rippleTint) {
        icon(v, res, tint);
        v.setBackground(rippleOval(rippleTint));
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    }

    public static void toast(Context c, int res) {
        Toast.makeText(c, res, Toast.LENGTH_SHORT).show();
    }

    public static void toast(Context c, String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }

    public static void copy(Context c, CharSequence text) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("iChat", text));
        }
    }

    public static void hideKeyboard(Activity a) {
        View v = a.getCurrentFocus();
        if (v != null) {
            InputMethodManager im =
                (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) {
                im.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        }
    }

    public static void showKeyboard(Activity a, View v) {
        v.requestFocus();
        InputMethodManager im =
            (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) {
            im.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    public static void setText(TextView t, CharSequence s) {
        t.setText(s);
    }

    /** Light haptic tick on the given view, if the user has haptics enabled. */
    public static void tick(View v) {
        if (Prefs.haptics(v.getContext())) {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }
}
