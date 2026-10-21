package com.userlabs.ichat;

import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared plumbing: theme, palette, rebuild-on-style-change and tiny view factories. */
public abstract class BaseActivity extends AppCompatActivity {

    protected Look look;
    protected float dens;
    private int revSeen;

    /** Address whose per-chat style should be used, or null for the global look. */
    protected String lookAddress() {
        return null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Look.theme(this);
        super.onCreate(savedInstanceState);
        dens = Ui.density(this);
        look = Look.get(this, lookAddress());
        revSeen = Prefs.rev();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Prefs.rev() != revSeen) {
            revSeen = Prefs.rev();
            recreate();
        }
    }

    /** Call after changing a style setting from this very screen so it is not rebuilt needlessly. */
    protected void markSeen() {
        revSeen = Prefs.rev();
    }

    protected int dp(float v) {
        return (int) (v * dens + 0.5f);
    }

    protected LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    protected LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
    }

    protected ImageView iconBtn(int res, int tint) {
        ImageView v = new ImageView(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(44), dp(44)));
        v.setPadding(dp(10), dp(10), dp(10), dp(10));
        Ui.iconButton(v, res, tint, Look.alpha(look.text, 0x22));
        return v;
    }

    protected TextView tv(CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) {
            t.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return t;
    }

    protected TextView oneLine(CharSequence s, float sp, int color, boolean bold) {
        TextView t = tv(s, sp, color, bold);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    protected LinearLayout headerBar() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(4), 0, dp(4), 0);
        h.setLayoutParams(lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
        return h;
    }

    protected View hline() {
        View v = new View(this);
        v.setBackgroundColor(look.divider);
        v.setLayoutParams(lp(LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    protected View spacer(int hDp) {
        View v = new View(this);
        v.setLayoutParams(lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(hDp)));
        return v;
    }
}
