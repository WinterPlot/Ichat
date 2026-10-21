package com.userlabs.ichat;

import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

/** Messaging insights computed on-device: totals, activity charts and top contacts. */
public class StatsActivity extends BaseActivity {

    static class Top {
        String address;
        int count;
        String name;
        Contacts.Info info;
    }

    static class Stats {
        int total;
        int sent;
        int received;
        long first = Long.MAX_VALUE;
        int[] hours = new int[24];
        int[] weekday = new int[7];
        int[] daySent = new int[14];
        int[] dayRecv = new int[14];
        ArrayList<Top> top = new ArrayList<Top>();
    }

    private LinearLayout content;
    private ProgressBar progress;

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
        TextView t = oneLine(getString(R.string.menu_insights), 20, look.text, true);
        t.setPadding(dp(6), 0, 0, 0);
        bar.addView(t, weight(1f));
        root.addView(bar);
        root.addView(hline());

        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(32));
        sv.addView(content);
        progress = new ProgressBar(this);
        content.addView(progress, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(80)));
        root.addView(sv, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        Look.window(this, look, look.bg);

        new AsyncTask<Void, Void, Stats>() {
            @Override
            protected Stats doInBackground(Void... params) {
                return compute();
            }

            @Override
            protected void onPostExecute(Stats s) {
                show(s);
            }
        }.execute();
    }

    private Stats compute() {
        Stats s = new Stats();
        HashMap<String, Top> map = new HashMap<String, Top>();
        TimeZone tz = TimeZone.getDefault();
        long now = System.currentTimeMillis();
        long today = (now + tz.getOffset(now)) / 86400000L;
        Cursor c = null;
        try {
            c = getContentResolver().query(Telephony.Sms.CONTENT_URI,
                new String[] { "type", "date", "address" }, null, null, null);
            while (c != null && c.moveToNext()) {
                int type = c.getInt(0);
                if (type == 3) {
                    continue; // drafts
                }
                long date = c.getLong(1);
                boolean recv = type == 1;
                s.total++;
                if (recv) {
                    s.received++;
                } else {
                    s.sent++;
                }
                if (date < s.first) {
                    s.first = date;
                }
                long local = date + tz.getOffset(date);
                int hour = (int) ((local / 3600000L) % 24L);
                if (hour < 0) {
                    hour += 24;
                }
                s.hours[hour]++;
                long day = local / 86400000L;
                int wd = (int) ((day + 3L) % 7L);
                if (wd < 0) {
                    wd += 7;
                }
                s.weekday[wd]++;
                long ago = today - day;
                if (ago >= 0 && ago < 14) {
                    int idx = 13 - (int) ago;
                    if (recv) {
                        s.dayRecv[idx]++;
                    } else {
                        s.daySent[idx]++;
                    }
                }
                String addr = c.getString(2);
                if (addr != null) {
                    String k = Prefs.key(addr);
                    Top t = map.get(k);
                    if (t == null) {
                        t = new Top();
                        t.address = addr;
                        map.put(k, t);
                    }
                    t.count++;
                }
            }
        } catch (Exception e) {
            // no permission: empty stats
        } finally {
            if (c != null) {
                c.close();
            }
        }
        s.top.addAll(map.values());
        Collections.sort(s.top, new Comparator<Top>() {
            @Override
            public int compare(Top x, Top y) {
                return y.count - x.count;
            }
        });
        while (s.top.size() > 6) {
            s.top.remove(s.top.size() - 1);
        }
        for (int i = 0; i < s.top.size(); i++) {
            Top t = s.top.get(i);
            t.info = Contacts.lookup(this, t.address);
            t.name = t.info.name;
        }
        return s;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(Ui.rect(look.surface, dp(18)));
        LinearLayout.LayoutParams p = lp(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(12), 0, 0);
        c.setLayoutParams(p);
        return c;
    }

    private View statTile(String value, String label, int color) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(14), dp(8), dp(14));
        t.setBackground(Ui.rect(look.surface, dp(18)));
        TextView v = tv(value, 24, color, true);
        v.setGravity(Gravity.CENTER);
        t.addView(v);
        TextView l = tv(label, 12, look.text2, false);
        l.setGravity(Gravity.CENTER);
        t.addView(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(4), 0, dp(4), 0);
        t.setLayoutParams(p);
        return t;
    }

    private void show(Stats s) {
        content.removeAllViews();
        if (s.total == 0) {
            TextView e = tv(getString(R.string.stats_empty), 15, look.text2, false);
            e.setGravity(Gravity.CENTER);
            e.setPadding(dp(24), dp(48), dp(24), 0);
            content.addView(e);
            return;
        }
        LinearLayout tiles = new LinearLayout(this);
        tiles.setOrientation(LinearLayout.HORIZONTAL);
        tiles.addView(statTile(String.valueOf(s.total), getString(R.string.stat_total), look.accent));
        tiles.addView(statTile(String.valueOf(s.received), getString(R.string.stat_received), look.text));
        tiles.addView(statTile(String.valueOf(s.sent), getString(R.string.stat_sent), look.text));
        LinearLayout.LayoutParams tp = lp(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.setMargins(-dp(4), 0, -dp(4), 0);
        content.addView(tiles, tp);

        long days = Math.max(1L, (System.currentTimeMillis() - s.first) / 86400000L);
        String since = new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(s.first));
        TextView sub = tv(getString(R.string.stats_since, since, String.valueOf(
            Math.round(s.total * 10.0 / days) / 10.0)), 13, look.text2, false);
        sub.setPadding(dp(4), dp(10), dp(4), 0);
        content.addView(sub);

        // last 14 days
        LinearLayout c1 = card();
        c1.addView(tv(getString(R.string.chart_14), 15, look.text, true));
        float[] recv = new float[14];
        float[] sent = new float[14];
        String[] lab = new String[14];
        Calendar cal = Calendar.getInstance();
        for (int i = 0; i < 14; i++) {
            recv[i] = s.dayRecv[i];
            sent[i] = s.daySent[i];
            Calendar x = (Calendar) cal.clone();
            x.add(Calendar.DAY_OF_YEAR, i - 13);
            lab[i] = (i % 2 == 1) ? String.valueOf(x.get(Calendar.DAY_OF_MONTH)) : "";
        }
        BarChartView ch1 = new BarChartView(this);
        ch1.setData(recv, sent, lab, look.accent, Look.hueShift(look.accent, 34f), -1,
            look.text2, look.divider);
        c1.addView(ch1);
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.addView(dot(look.accent));
        legend.addView(tv(getString(R.string.stat_received), 12, look.text2, false));
        View gap = new View(this);
        legend.addView(gap, lp(dp(14), 1));
        legend.addView(dot(Look.hueShift(look.accent, 34f)));
        legend.addView(tv(getString(R.string.stat_sent), 12, look.text2, false));
        legend.setGravity(Gravity.CENTER_VERTICAL);
        c1.addView(legend);
        content.addView(c1);

        // hours
        int peak = 0;
        float[] hrs = new float[24];
        String[] hl = new String[24];
        for (int i = 0; i < 24; i++) {
            hrs[i] = s.hours[i];
            if (s.hours[i] > s.hours[peak]) {
                peak = i;
            }
            hl[i] = (i % 6 == 0) ? String.valueOf(i) : "";
        }
        LinearLayout c2 = card();
        c2.addView(tv(getString(R.string.chart_hours), 15, look.text, true));
        BarChartView ch2 = new BarChartView(this);
        ch2.setData(hrs, null, hl, look.accent, look.accent, peak, look.text2, look.divider);
        c2.addView(ch2);
        c2.addView(tv(getString(R.string.peak_hour, peak, (peak + 1) % 24), 13, look.text2, false));
        content.addView(c2);

        // weekday
        String[] sym = new DateFormatSymbols(Locale.getDefault()).getShortWeekdays();
        int[] order = new int[] { 2, 3, 4, 5, 6, 7, 1 };
        float[] wd = new float[7];
        String[] wl = new String[7];
        int wpeak = 0;
        for (int i = 0; i < 7; i++) {
            wd[i] = s.weekday[i];
            if (s.weekday[i] > s.weekday[wpeak]) {
                wpeak = i;
            }
            String n = sym[order[i]];
            wl[i] = n.length() > 3 ? n.substring(0, 3) : n;
        }
        LinearLayout c3 = card();
        c3.addView(tv(getString(R.string.chart_weekday), 15, look.text, true));
        BarChartView ch3 = new BarChartView(this);
        ch3.setData(wd, null, wl, look.accent, look.accent, wpeak, look.text2, look.divider);
        c3.addView(ch3);
        content.addView(c3);

        // top contacts
        LinearLayout c4 = card();
        c4.addView(tv(getString(R.string.chart_top), 15, look.text, true));
        int max = s.top.size() > 0 ? s.top.get(0).count : 1;
        for (int i = 0; i < s.top.size(); i++) {
            Top t = s.top.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, 0);
            ImageView av = new ImageView(this);
            av.setScaleType(ImageView.ScaleType.FIT_XY);
            av.setImageDrawable(Contacts.avatar(t.address, t.info));
            row.addView(av, lp(dp(36), dp(36)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cp = weight(1f);
            cp.setMargins(dp(12), 0, 0, 0);
            row.addView(col, cp);
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.addView(oneLine(t.name, 14, look.text, false), weight(1f));
            line.addView(tv(String.valueOf(t.count), 13, look.text2, true));
            col.addView(line);
            LinearLayout track = new LinearLayout(this);
            track.setBackground(Ui.rect(look.surface2, dp(3)));
            View fill = new View(this);
            fill.setBackground(Ui.rect(look.accent, dp(3)));
            track.addView(fill, new LinearLayout.LayoutParams(0, dp(6), Math.max(0.02f, t.count / (float) max)));
            View rest = new View(this);
            track.addView(rest, new LinearLayout.LayoutParams(0, dp(6),
                Math.max(0f, 1f - t.count / (float) max)));
            LinearLayout.LayoutParams trp = lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(6));
            trp.setMargins(0, dp(5), 0, 0);
            col.addView(track, trp);
            c4.addView(row);
        }
        content.addView(c4);
    }

    private View dot(int color) {
        View v = new View(this);
        v.setBackground(Ui.oval(color));
        LinearLayout.LayoutParams p = lp(dp(8), dp(8));
        p.setMargins(0, 0, dp(5), 0);
        v.setLayoutParams(p);
        return v;
    }
}
