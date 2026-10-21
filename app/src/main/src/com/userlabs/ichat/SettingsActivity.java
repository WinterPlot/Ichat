package com.userlabs.ichat;

import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Telephony;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;

/** Settings: appearance shortcuts, messaging behaviour, notifications, privacy and about. */
public class SettingsActivity extends BaseActivity {

    private LinearLayout col;
    private TextView defaultSub;
    private TextView blockedSub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(look.bg);
        LinearLayout bar = headerBar();
        ImageView back = iconBtn(R.drawable.ic_back, look.text);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        bar.addView(back);
        TextView t = oneLine(getString(R.string.menu_settings), 20, look.text, true);
        t.setPadding(dp(6), 0, 0, 0);
        bar.addView(t, weight(1f));
        root.addView(bar);
        root.addView(hline());

        ScrollView sv = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(32));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        Look.window(this, look, look.bg);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDynamic();
    }

    private void refreshDynamic() {
        if (defaultSub != null) {
            defaultSub.setText(SmsUtil.isDefault(this) ? R.string.default_yes : R.string.default_no);
        }
        if (blockedSub != null) {
            blockedSub.setText(getString(R.string.n_blocked, Prefs.blockedKeys(this).size()));
        }
    }

    private void build() {
        col.addView(section(R.string.set_sec_appearance));
        col.addView(link(R.drawable.ic_palette, getString(R.string.menu_chat_style),
            getString(R.string.set_style_sub), new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(SettingsActivity.this, StyleActivity.class));
                }
            }, null));
        col.addView(toggle(getString(R.string.set_group), getString(R.string.set_group_sub),
            "group_msgs", true, true));
        col.addView(toggle(getString(R.string.set_avatars), getString(R.string.set_avatars_sub),
            "show_avatars", true, true));
        col.addView(toggle(getString(R.string.set_time_always), getString(R.string.set_time_always_sub),
            "time_always", false, true));
        col.addView(toggle(getString(R.string.set_time24), getString(R.string.set_time24_sub),
            "time24", true, true));

        col.addView(section(R.string.set_sec_messaging));
        col.addView(toggle(getString(R.string.set_smart), getString(R.string.set_smart_sub),
            "smart_replies", true, false));
        col.addView(link(R.drawable.ic_edit, getString(R.string.set_quick),
            getString(R.string.set_quick_sub), new Runnable() {
                @Override
                public void run() {
                    editQuickReplies();
                }
            }, null));
        col.addView(toggle(getString(R.string.set_haptics), getString(R.string.set_haptics_sub),
            "haptics", true, false));

        col.addView(section(R.string.set_sec_sim));
        col.addView(toggle(getString(R.string.set_sim_remember), getString(R.string.set_sim_remember_sub),
            "sim_remember", true, false));
        col.addView(link(R.drawable.ic_sim, getString(R.string.set_sim_perm),
            getString(Sims.hasPermission(this) ? R.string.set_sim_perm_on : R.string.set_sim_perm_sub),
            new Runnable() {
                @Override
                public void run() {
                    if (Sims.hasPermission(SettingsActivity.this)) {
                        Ui.toast(SettingsActivity.this, R.string.set_sim_perm_on);
                    } else if (Build.VERSION.SDK_INT >= 23) {
                        requestPermissions(new String[] { Manifest.permission.READ_PHONE_STATE }, 78);
                    } else {
                        Ui.toast(SettingsActivity.this, R.string.set_sim_unsupported);
                    }
                }
            }, null));

        col.addView(section(R.string.set_sec_notifications));
        col.addView(toggle(getString(R.string.set_hide), getString(R.string.set_hide_sub),
            "hide_notif", false, false));
        col.addView(link(R.drawable.ic_bell, getString(R.string.set_system_notif),
            getString(R.string.set_system_notif_sub), new Runnable() {
                @Override
                public void run() {
                    openSystemNotifications();
                }
            }, null));

        col.addView(section(R.string.set_sec_privacy));
        TextView[] sub = new TextView[1];
        col.addView(link(R.drawable.ic_block, getString(R.string.set_blocked), "", new Runnable() {
            @Override
            public void run() {
                showBlocked();
            }
        }, sub));
        blockedSub = sub[0];
        col.addView(link(R.drawable.ic_star, getString(R.string.menu_starred),
            getString(R.string.set_starred_sub), new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(SettingsActivity.this, StarredActivity.class));
                }
            }, null));
        col.addView(link(R.drawable.ic_bar_chart, getString(R.string.menu_insights),
            getString(R.string.set_insights_sub), new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(SettingsActivity.this, StatsActivity.class));
                }
            }, null));

        col.addView(section(R.string.set_sec_default));
        TextView[] ds = new TextView[1];
        col.addView(link(R.drawable.ic_chat, getString(R.string.set_default_app), "", new Runnable() {
            @Override
            public void run() {
                Intent i = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                i.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
                startActivity(i);
            }
        }, ds));
        defaultSub = ds[0];

        col.addView(section(R.string.set_sec_about));
        LinearLayout about = new LinearLayout(this);
        about.setOrientation(LinearLayout.VERTICAL);
        about.setPadding(dp(18), dp(6), dp(18), dp(6));
        about.addView(tv(getString(R.string.about_title), 16, look.text, true));
        TextView ab = tv(getString(R.string.about_body), 13, look.text2, false);
        ab.setPadding(0, dp(4), 0, 0);
        about.addView(ab);
        col.addView(about);
        refreshDynamic();
    }

    private View section(int res) {
        TextView t = tv(getString(res), 12, look.accent, true);
        t.setAllCaps(true);
        t.setPadding(dp(18), dp(22), dp(18), dp(6));
        return t;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 78) {
            Sims.invalidate();
            recreate();
        }
    }

    private View link(int icon, String title, String sub, final Runnable action, TextView[] subOut) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(12), dp(18), dp(12));
        row.setBackground(Ui.ripple(Look.alpha(look.text, 0x14), null, 0f));
        ImageView ic = new ImageView(this);
        Ui.icon(ic, icon, look.text2);
        row.addView(ic, lp(dp(24), dp(24)));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = weight(1f);
        cp.setMargins(dp(18), 0, 0, 0);
        row.addView(c, cp);
        c.addView(tv(title, 16, look.text, false));
        TextView s = tv(sub, 13, look.text2, false);
        c.addView(s);
        if (subOut != null) {
            subOut[0] = s;
        }
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        return row;
    }

    private View toggle(String title, String sub, final String key, boolean def, final boolean appearance) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(12), dp(18), dp(12));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        row.addView(c, weight(1f));
        c.addView(tv(title, 16, look.text, false));
        c.addView(tv(sub, 13, look.text2, false));
        final ToggleView tg = new ToggleView(this);
        tg.setColors(look.accent, look.dark ? 0xFF4A505A : 0xFFC5CAD3);
        tg.setOn(Prefs.getBool(this, key, def), false);
        tg.setListener(new ToggleView.Listener() {
            @Override
            public void onToggled(ToggleView v, boolean on) {
                Prefs.putBool(SettingsActivity.this, key, on);
                if (appearance) {
                    Prefs.touch();
                    markSeen();
                }
            }
        });
        LinearLayout.LayoutParams tp = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.setMargins(dp(12), 0, 0, 0);
        row.addView(tg, tp);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tg.performClickToggle();
            }
        });
        return row;
    }

    private void editQuickReplies() {
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setMinLines(4);
        e.setGravity(Gravity.TOP);
        StringBuilder sb = new StringBuilder();
        String[] q = Prefs.quickReplies(this);
        for (int i = 0; i < q.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(q[i]);
        }
        e.setText(sb.toString());
        e.setTextColor(look.text);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(e, lp(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
            .setTitle(R.string.set_quick)
            .setMessage(R.string.quick_hint)
            .setView(box)
            .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Prefs.putString(SettingsActivity.this, "quick_replies", e.getText().toString());
                }
            })
            .setNeutralButton(R.string.reset, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Prefs.remove(SettingsActivity.this, "quick_replies");
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void openSystemNotifications() {
        try {
            Intent i = new Intent();
            if (Build.VERSION.SDK_INT >= 26) {
                i.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(android.net.Uri.parse("package:" + getPackageName()));
            }
            startActivity(i);
        } catch (Exception e) {
            Ui.toast(this, R.string.no_app);
        }
    }

    private void showBlocked() {
        final ArrayList<String> keys = Prefs.blockedKeys(this);
        if (keys.size() == 0) {
            Ui.toast(this, R.string.no_blocked);
            return;
        }
        String[] labels = new String[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            Contacts.Info info = Contacts.cached(keys.get(i));
            labels[i] = (info != null && info.found) ? info.name + "  (" + keys.get(i) + ")" : keys.get(i);
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.set_blocked)
            .setItems(labels, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Prefs.unblockKey(SettingsActivity.this, keys.get(which));
                    Ui.toast(SettingsActivity.this, R.string.unblocked);
                    refreshDynamic();
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }
}
