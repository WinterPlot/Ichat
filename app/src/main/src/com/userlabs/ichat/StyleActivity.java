package com.userlabs.ichat;

import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** The style studio: bubble style, roundness, text size, accent, wallpaper and theme with a live preview. */
public class StyleActivity extends BaseActivity {

    private String address; // null = global
    private int curStyle;
    private int curAccent;
    private int curWall;
    private int curRound;
    private int curText;

    private LinearLayout previewHost;
    private LinearLayout stylesRow;
    private LinearLayout accentRow;
    private LinearLayout wallRow;
    private LinearLayout themeRow;
    private TextView roundValue;
    private TextView textValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        address = getIntent().getStringExtra("address");
        super.onCreate(savedInstanceState);
        loadCurrent();
        setContentView(buildUi());
        Look.window(this, look, look.bg);
        refreshAll();
    }

    private void loadCurrent() {
        curStyle = Prefs.bubbleStyle(this, address);
        curAccent = Prefs.accent(this, address);
        curWall = Prefs.wallpaper(this, address);
        curRound = Prefs.roundness(this, address);
        curText = Prefs.textSize(this);
    }

    private Look previewLook() {
        return look.withAccent(curAccent);
    }

    // ---------------------------------------------------------------- UI

    private View buildUi() {
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
        String title = getString(R.string.style_title);
        if (address != null) {
            title = getString(R.string.style_title_chat, SmsUtilName());
        }
        TextView t = oneLine(title, 20, look.text, true);
        t.setPadding(dp(6), 0, 0, 0);
        bar.addView(t, weight(1f));
        ImageView reset = iconBtn(R.drawable.ic_restore, look.text);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmReset();
            }
        });
        bar.addView(reset);
        root.addView(bar);
        root.addView(hline());

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, 0, dp(32));
        sv.addView(col);
        root.addView(sv);

        if (address != null) {
            TextView note = tv(getString(R.string.style_chat_note), 13, look.text2, false);
            note.setPadding(dp(16), dp(10), dp(16), 0);
            col.addView(note);
        }

        previewHost = new LinearLayout(this);
        previewHost.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pp = lp(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        pp.setMargins(dp(14), dp(12), dp(14), dp(4));
        previewHost.setLayoutParams(pp);
        previewHost.setClipToOutline(true);
        previewHost.setOutlineProvider(roundOutline(dp(18)));
        col.addView(previewHost);

        col.addView(sectionTitle(getString(R.string.sec_bubbles)));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        stylesRow = new LinearLayout(this);
        stylesRow.setOrientation(LinearLayout.HORIZONTAL);
        stylesRow.setPadding(dp(10), dp(4), dp(10), dp(4));
        hs.addView(stylesRow);
        col.addView(hs);

        col.addView(sectionTitle(getString(R.string.sec_shape)));
        col.addView(sliderRow(getString(R.string.roundness), 0, 100, curRound, true));
        if (address == null) {
            col.addView(sliderRow(getString(R.string.text_size), 12, 24, curText, false));
        }

        col.addView(sectionTitle(getString(R.string.sec_accent)));
        HorizontalScrollView as = new HorizontalScrollView(this);
        as.setHorizontalScrollBarEnabled(false);
        accentRow = new LinearLayout(this);
        accentRow.setOrientation(LinearLayout.HORIZONTAL);
        accentRow.setPadding(dp(14), dp(6), dp(14), dp(6));
        as.addView(accentRow);
        col.addView(as);

        col.addView(sectionTitle(getString(R.string.sec_wallpaper)));
        HorizontalScrollView ws = new HorizontalScrollView(this);
        ws.setHorizontalScrollBarEnabled(false);
        wallRow = new LinearLayout(this);
        wallRow.setOrientation(LinearLayout.HORIZONTAL);
        wallRow.setPadding(dp(10), dp(4), dp(10), dp(4));
        ws.addView(wallRow);
        col.addView(ws);

        if (address == null) {
            col.addView(sectionTitle(getString(R.string.sec_theme)));
            themeRow = new LinearLayout(this);
            themeRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams tp = lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            tp.setMargins(dp(14), dp(4), dp(14), 0);
            themeRow.setLayoutParams(tp);
            col.addView(themeRow);
        }
        return root;
    }

    private String SmsUtilName() {
        Contacts.Info i = Contacts.cached(address);
        return (i != null && i.name != null && i.name.length() > 0) ? i.name : address;
    }

    private TextView sectionTitle(String s) {
        TextView t = tv(s, 13, look.text2, true);
        t.setAllCaps(true);
        t.setPadding(dp(18), dp(20), dp(18), dp(6));
        return t;
    }

    private ViewOutlineProvider roundOutline(final int r) {
        return new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        };
    }

    private void refreshAll() {
        buildPreview();
        buildStyles();
        buildAccents();
        buildWalls();
        buildTheme();
    }

    // ---------------------------------------------------------------- preview

    private TextView sample(String text, boolean out, boolean jp, boolean jn, Look k, int style,
                            float sp, int round) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(BubbleDrawable.textColor(style, out, k));
        t.setTypeface(BubbleDrawable.typeface(style));
        t.setBackground(new BubbleDrawable(style, out, jp, jn, dens, round, k));
        t.setMaxWidth(dp(250));
        return t;
    }

    private void addSample(LinearLayout host, String text, boolean out, boolean jp, boolean jn,
                           Look k, int style, float sp, int round, String meta) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setGravity(out ? Gravity.RIGHT : Gravity.LEFT);
        wrap.setPadding(dp(10), jp ? dp(1) : dp(6), dp(10), 0);
        wrap.addView(sample(text, out, jp, jn, k, style, sp, round));
        if (meta != null) {
            TextView m = tv(meta, 11, WallpaperDrawable.isDark(curWall, k) ? 0xCCFFFFFF : 0xCC333B47,
                false);
            m.setPadding(dp(6), dp(2), dp(6), 0);
            wrap.addView(m);
        }
        host.addView(wrap, lp(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void buildPreview() {
        previewHost.removeAllViews();
        Look k = previewLook();
        previewHost.setBackground(new WallpaperDrawable(curWall, k, dens));
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(0, dp(10), 0, dp(14));

        TextView chip = tv(getString(R.string.today), 12, k.text2, true);
        chip.setPadding(dp(12), dp(4), dp(12), dp(4));
        chip.setBackground(Ui.rect(Look.alpha(k.surface, 0xE6), dp(12)));
        LinearLayout.LayoutParams cp = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER_HORIZONTAL;
        cp.setMargins(0, 0, 0, dp(4));
        inner.addView(chip, cp);

        addSample(inner, getString(R.string.prev_1), false, false, true, k, curStyle, curText, curRound, null);
        addSample(inner, getString(R.string.prev_2), false, true, false, k, curStyle, curText, curRound,
            "18:42");
        addSample(inner, getString(R.string.prev_3), true, false, true, k, curStyle, curText, curRound, null);
        addSample(inner, getString(R.string.prev_4), true, true, false, k, curStyle, curText, curRound,
            "18:43  \u00B7  \u2713\u2713");
        addSample(inner, getString(R.string.prev_5), false, false, false, k, curStyle, curText, curRound,
            null);
        previewHost.addView(inner);
    }

    // ---------------------------------------------------------------- styles row

    private void buildStyles() {
        stylesRow.removeAllViews();
        Look k = previewLook();
        for (int i = 0; i < BubbleDrawable.COUNT; i++) {
            final int style = i;
            boolean on = i == curStyle;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setPadding(dp(8), dp(8), dp(8), dp(6));
            tile.setBackground(Ui.rectStroke(look.surface, on ? curAccent : look.divider,
                on ? dp(2) : dp(1), dp(14)));
            LinearLayout.LayoutParams tp = lp(dp(138), LinearLayout.LayoutParams.WRAP_CONTENT);
            tp.setMargins(dp(4), 0, dp(4), 0);
            tile.setLayoutParams(tp);

            FrameLayout stage = new FrameLayout(this);
            stage.setBackground(Ui.rect(look.bg, dp(10)));
            LinearLayout in = new LinearLayout(this);
            in.setOrientation(LinearLayout.VERTICAL);
            in.setPadding(dp(4), dp(6), dp(4), dp(6));
            addMini(in, "Hello!", false, k, style);
            addMini(in, "Hi \uD83D\uDE0A", true, k, style);
            stage.addView(in);
            tile.addView(stage);

            TextView label = oneLine(getString(BubbleDrawable.nameRes(i)), 12,
                on ? curAccent : look.text, on);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(6), 0, 0);
            tile.addView(label);
            tile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    curStyle = style;
                    Prefs.setStyleValue(StyleActivity.this, address, "s", style);
                    markSeen();
                    buildPreview();
                    buildStyles();
                    Ui.tick(v);
                }
            });
            stylesRow.addView(tile);
        }
    }

    private void addMini(LinearLayout host, String text, boolean out, Look k, int style) {
        LinearLayout w = new LinearLayout(this);
        w.setOrientation(LinearLayout.VERTICAL);
        w.setGravity(out ? Gravity.RIGHT : Gravity.LEFT);
        w.setPadding(0, dp(2), 0, dp(2));
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11);
        t.setTextColor(BubbleDrawable.textColor(style, out, k));
        t.setTypeface(BubbleDrawable.typeface(style));
        t.setBackground(new BubbleDrawable(style, out, false, false, dens, curRound, k));
        w.addView(t);
        host.addView(w, lp(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    // ---------------------------------------------------------------- sliders

    private View sliderRow(String label, final int min, final int max, int value, final boolean round) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(18), dp(4), dp(18), dp(2));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.addView(tv(label, 15, look.text, false), weight(1f));
        final TextView val = tv(String.valueOf(value), 14, look.text2, false);
        head.addView(val);
        row.addView(head);
        if (round) {
            roundValue = val;
        } else {
            textValue = val;
        }
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(value - min);
        sb.setProgressTintList(ColorStateList.valueOf(curAccent));
        sb.setThumbTintList(ColorStateList.valueOf(curAccent));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = progress + min;
                val.setText(String.valueOf(v));
                if (round) {
                    curRound = v;
                } else {
                    curText = v;
                }
                if (fromUser) {
                    buildPreview();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (round) {
                    Prefs.setStyleValue(StyleActivity.this, address, "r", curRound);
                } else {
                    Prefs.setTextSize(StyleActivity.this, curText);
                }
                markSeen();
                buildStyles();
            }
        });
        row.addView(sb);
        return row;
    }

    // ---------------------------------------------------------------- accent

    private void buildAccents() {
        accentRow.removeAllViews();
        boolean matched = false;
        for (int i = 0; i < Look.ACCENTS.length; i++) {
            final int c = Look.ACCENTS[i];
            boolean on = (c | 0xFF000000) == (curAccent | 0xFF000000);
            if (on) {
                matched = true;
            }
            accentRow.addView(swatch(c, on, false));
        }
        accentRow.addView(swatch(curAccent, !matched, true));
    }

    private View swatch(final int color, boolean on, final boolean custom) {
        FrameLayout f = new FrameLayout(this);
        LinearLayout.LayoutParams p = lp(dp(46), dp(46));
        p.setMargins(dp(4), 0, dp(4), 0);
        f.setLayoutParams(p);
        View dot = new View(this);
        Drawable bg;
        if (custom) {
            bg = Ui.rectStroke(look.surface2, look.text2, dp(1), dp(20));
            ((android.graphics.drawable.GradientDrawable) bg).setShape(
                android.graphics.drawable.GradientDrawable.OVAL);
        } else {
            bg = Ui.oval(color | 0xFF000000);
        }
        dot.setBackground(bg);
        FrameLayout.LayoutParams dpm = new FrameLayout.LayoutParams(dp(38), dp(38));
        dpm.gravity = Gravity.CENTER;
        f.addView(dot, dpm);
        if (custom) {
            ImageView plus = new ImageView(this);
            Ui.icon(plus, on ? R.drawable.ic_check : R.drawable.ic_palette, look.text);
            FrameLayout.LayoutParams pm = new FrameLayout.LayoutParams(dp(22), dp(22));
            pm.gravity = Gravity.CENTER;
            f.addView(plus, pm);
            if (on) {
                dot.setBackground(Ui.oval(color | 0xFF000000));
                Ui.icon(plus, R.drawable.ic_check,
                    Look.lum(color) > 0.62f ? 0xFF111111 : 0xFFFFFFFF);
            }
        } else if (on) {
            ImageView chk = new ImageView(this);
            Ui.icon(chk, R.drawable.ic_check, Look.lum(color) > 0.62f ? 0xFF111111 : 0xFFFFFFFF);
            FrameLayout.LayoutParams cm = new FrameLayout.LayoutParams(dp(20), dp(20));
            cm.gravity = Gravity.CENTER;
            f.addView(chk, cm);
        }
        f.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (custom) {
                    pickCustomColor();
                } else {
                    setAccent(color | 0xFF000000);
                }
            }
        });
        return f;
    }

    private void setAccent(int c) {
        curAccent = c;
        Prefs.setStyleValue(this, address, "c", c);
        markSeen();
        refreshAll();
    }

    private void pickCustomColor() {
        final int[] rgb = new int[] {
            (curAccent >> 16) & 0xFF, (curAccent >> 8) & 0xFF, curAccent & 0xFF
        };
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(16), dp(22), dp(4));
        final View swatch = new View(this);
        swatch.setBackground(Ui.rect(0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2], dp(12)));
        box.addView(swatch, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)));
        final String[] names = new String[] { "R", "G", "B" };
        final int[] tints = new int[] { 0xFFE53935, 0xFF43A047, 0xFF1E88E5 };
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.addView(tv(names[i], 14, look.text, true), lp(dp(22), LinearLayout.LayoutParams.WRAP_CONTENT));
            SeekBar sb = new SeekBar(this);
            sb.setMax(255);
            sb.setProgress(rgb[i]);
            sb.setProgressTintList(ColorStateList.valueOf(tints[i]));
            sb.setThumbTintList(ColorStateList.valueOf(tints[i]));
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean u) {
                    rgb[idx] = p;
                    swatch.setBackground(Ui.rect(
                        0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2], dp(12)));
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            r.addView(sb, weight(1f));
            LinearLayout.LayoutParams rp = lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.setMargins(0, dp(8), 0, 0);
            box.addView(r, rp);
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.custom_color)
            .setView(box)
            .setPositiveButton(R.string.apply, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    setAccent(0xFF000000 | (rgb[0] << 16) | (rgb[1] << 8) | rgb[2]);
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    // ---------------------------------------------------------------- wallpaper

    private void buildWalls() {
        wallRow.removeAllViews();
        Look k = previewLook();
        for (int i = 0; i < WallpaperDrawable.COUNT; i++) {
            final int id = i;
            boolean on = i == curWall;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cp = lp(dp(86), LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.setMargins(dp(5), 0, dp(5), 0);
            cell.setLayoutParams(cp);

            FrameLayout frame = new FrameLayout(this);
            frame.setBackground(Ui.rectStroke(0, on ? curAccent : 0, dp(3), dp(14)));
            frame.setPadding(dp(3), dp(3), dp(3), dp(3));
            View wall = new View(this);
            wall.setBackground(new WallpaperDrawable(i, k, dens));
            wall.setClipToOutline(true);
            wall.setOutlineProvider(roundOutline(dp(11)));
            frame.addView(wall, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(112)));
            // two tiny bubbles on the tile
            LinearLayout mini = new LinearLayout(this);
            mini.setOrientation(LinearLayout.VERTICAL);
            mini.setPadding(dp(8), dp(14), dp(8), 0);
            View b1 = new View(this);
            b1.setBackground(Ui.rect(look.inBubble, dp(7)));
            LinearLayout.LayoutParams m1 = lp(dp(38), dp(14));
            mini.addView(b1, m1);
            View b2 = new View(this);
            b2.setBackground(Ui.rect(curAccent, dp(7)));
            LinearLayout.LayoutParams m2 = lp(dp(32), dp(14));
            m2.gravity = Gravity.RIGHT;
            m2.setMargins(0, dp(6), 0, 0);
            mini.addView(b2, m2);
            frame.addView(mini, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
            cell.addView(frame);

            TextView label = oneLine(getString(WallpaperDrawable.nameRes(i)), 12,
                on ? curAccent : look.text, on);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(4), 0, 0);
            cell.addView(label);
            cell.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    curWall = id;
                    Prefs.setStyleValue(StyleActivity.this, address, "w", id);
                    markSeen();
                    buildPreview();
                    buildWalls();
                    Ui.tick(v);
                }
            });
            wallRow.addView(cell);
        }
    }

    // ---------------------------------------------------------------- theme

    private void buildTheme() {
        if (themeRow == null) {
            return;
        }
        themeRow.removeAllViews();
        int[] labels = new int[] { R.string.theme_auto, R.string.theme_light, R.string.theme_dark,
            R.string.theme_amoled };
        int mode = Prefs.themeMode(this);
        for (int i = 0; i < labels.length; i++) {
            final int m = i;
            boolean on = mode == i;
            TextView t = tv(getString(labels[i]), 14, on ? look.onAccent : look.text, on);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(11), 0, dp(11));
            t.setBackground(Ui.rect(on ? curAccent : look.surface2, dp(12)));
            if (on) {
                t.setTextColor(Look.lum(curAccent) > 0.62f ? 0xFF111111 : 0xFFFFFFFF);
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            p.setMargins(dp(3), 0, dp(3), 0);
            t.setLayoutParams(p);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.setThemeMode(StyleActivity.this, m);
                    recreate();
                }
            });
            themeRow.addView(t);
        }
    }

    // ---------------------------------------------------------------- reset

    private void confirmReset() {
        int msg = address == null ? R.string.style_reset_all : R.string.style_reset_chat;
        new AlertDialog.Builder(this)
            .setMessage(msg)
            .setPositiveButton(R.string.reset, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    if (address == null) {
                        Prefs.remove(StyleActivity.this, "bubble_style");
                        Prefs.remove(StyleActivity.this, "accent");
                        Prefs.remove(StyleActivity.this, "wallpaper");
                        Prefs.remove(StyleActivity.this, "roundness");
                        Prefs.remove(StyleActivity.this, "text_size");
                        Prefs.touch();
                    } else {
                        Prefs.clearChatOverride(StyleActivity.this, address);
                    }
                    loadCurrent();
                    markSeen();
                    refreshAll();
                    recreate();
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }
}
