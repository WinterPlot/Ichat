package com.userlabs.ichat;

import android.Manifest;
import androidx.appcompat.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Telephony;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Conversation list: pinned chats, filters, search (names and message text), multi-select. */
public class MainActivity extends BaseActivity {

    static class Conversation {
        int kind; // 0 conversation, 1 section header, 2 message search hit
        long threadId;
        String address;
        String name;
        String snippet;
        long date;
        int unread;
        int lastType;
        boolean pinned;
        boolean muted;
        boolean blocked;
        String draft;
        long msgId;
        String headerText;
        Contacts.Info info;
        int subId = -1;
    }

    static class Result {
        ArrayList<Conversation> list = new ArrayList<Conversation>();
        boolean anyBlocked;
    }

    static class Holder {
        ImageView avatar;
        TextView name;
        TextView time;
        TextView snippet;
        ImageView mute;
        ImageView pin;
        TextView badge;
        TextView sim;
    }

    private static final int REQ_PERMS = 1;
    private static final int F_ALL = 0;
    private static final int F_UNREAD = 1;
    private static final int F_PINNED = 2;
    private static final int F_BLOCKED = 3;
    private static final String[] PERMS = {
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_CONTACTS
    };

    private final ArrayList<Conversation> items = new ArrayList<Conversation>();
    private final HashSet<String> selected = new HashSet<String>();
    private ConversationAdapter adapter;
    private ListView list;
    private TextView empty;
    private LinearLayout banner;
    private LinearLayout chipRow;
    private LinearLayout titleBar;
    private LinearLayout searchBar;
    private LinearLayout selBar;
    private EditText searchInput;
    private TextView selCount;
    private int filter = F_ALL;
    private String query = "";
    private boolean anyBlocked;
    private boolean loading;
    private boolean pending;
    private boolean observing;
    private boolean askedPerms;

    private final ContentObserver observer = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            load();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        Look.window(this, look, look.bg);
    }

    // ---------------------------------------------------------------- UI construction

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(look.bg);

        FrameLayout headerHost = new FrameLayout(this);
        headerHost.setLayoutParams(lp(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        // normal title bar
        titleBar = headerBar();
        TextView title = tv(getString(R.string.app_name), 26, look.text, true);
        title.setPadding(dp(14), 0, 0, 0);
        titleBar.addView(title, weight(1f));
        ImageView searchBtn = iconBtn(R.drawable.ic_search, look.text);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSearch();
            }
        });
        titleBar.addView(searchBtn);
        final ImageView moreBtn = iconBtn(R.drawable.ic_more, look.text);
        moreBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMainMenu(moreBtn);
            }
        });
        titleBar.addView(moreBtn);
        headerHost.addView(titleBar);

        // search bar
        searchBar = headerBar();
        searchBar.setVisibility(View.GONE);
        ImageView sBack = iconBtn(R.drawable.ic_back, look.text);
        sBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSearch();
            }
        });
        searchBar.addView(sBack);
        searchInput = new EditText(this);
        searchInput.setHint(R.string.search_hint);
        searchInput.setHintTextColor(look.text2);
        searchInput.setTextColor(look.text);
        searchInput.setSingleLine(true);
        searchInput.setBackgroundColor(0);
        searchInput.setTextSize(17);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim();
                load();
            }
        });
        searchBar.addView(searchInput, weight(1f));
        ImageView sClear = iconBtn(R.drawable.ic_close, look.text2);
        sClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchInput.setText("");
            }
        });
        searchBar.addView(sClear);
        headerHost.addView(searchBar);

        // selection bar
        selBar = headerBar();
        selBar.setVisibility(View.GONE);
        selBar.setBackgroundColor(look.surface);
        ImageView selClose = iconBtn(R.drawable.ic_close, look.text);
        selClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearSelection();
            }
        });
        selBar.addView(selClose);
        selCount = tv("", 18, look.text, true);
        selCount.setPadding(dp(6), 0, 0, 0);
        selBar.addView(selCount, weight(1f));
        selBar.addView(selAction(R.drawable.ic_pin, 1));
        selBar.addView(selAction(R.drawable.ic_mail, 2));
        selBar.addView(selAction(R.drawable.ic_bell_off, 3));
        selBar.addView(selAction(R.drawable.ic_block, 4));
        selBar.addView(selAction(R.drawable.ic_delete, 5));
        headerHost.addView(selBar);
        root.addView(headerHost);

        // default-app banner
        banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(16), dp(10), dp(16), dp(10));
        banner.setBackground(Ui.rect(Look.alpha(look.accent, 0x28), 0f));
        TextView bt = tv(getString(R.string.banner_default), 14, look.text, false);
        banner.addView(bt, weight(1f));
        TextView bb = tv(getString(R.string.set_default), 14, look.accent, true);
        bb.setPadding(dp(12), dp(6), 0, dp(6));
        banner.addView(bb);
        banner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestDefault();
            }
        });
        banner.setVisibility(View.GONE);
        root.addView(banner);

        // filter chips
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        chipRow.setPadding(dp(12), dp(4), dp(12), dp(8));
        chipScroll.addView(chipRow);
        root.addView(chipScroll);

        // list + empty + fab
        FrameLayout body = new FrameLayout(this);
        body.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setCacheColorHint(0);
        list.setSelector(new ColorDrawable(0));
        adapter = new ConversationAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                onRowClick(items.get(pos));
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
                Conversation cv = items.get(pos);
                if (cv.kind != 0) {
                    return false;
                }
                Ui.tick(view);
                toggleSelect(cv);
                return true;
            }
        });
        body.addView(list, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        empty = tv("", 16, look.text2, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(32), dp(32), dp(32), dp(32));
        empty.setVisibility(View.GONE);
        empty.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasPermissions() && Build.VERSION.SDK_INT >= 23) {
                    requestPermissions(PERMS, REQ_PERMS);
                }
            }
        });
        body.addView(empty, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ImageView fab = new ImageView(this);
        fab.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        fab.setPadding(dp(16), dp(16), dp(16), dp(16));
        fab.setImageDrawable(Ui.tinted(this, R.drawable.ic_edit, look.onAccent));
        fab.setBackground(Ui.ripple(0x33FFFFFF, Ui.oval(look.accent), dp(28)));
        if (Build.VERSION.SDK_INT >= 21) {
            fab.setElevation(dp(6));
        }
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(dp(58), dp(58));
        fl.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        fl.setMargins(0, 0, dp(18), dp(20));
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, NewMessageActivity.class));
            }
        });
        body.addView(fab, fl);
        root.addView(body);

        rebuildChips();
        return root;
    }

    private ImageView selAction(int res, final int action) {
        ImageView v = iconBtn(res, look.text);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runSelectionAction(action);
            }
        });
        return v;
    }

    private void rebuildChips() {
        chipRow.removeAllViews();
        addChip(R.string.chip_all, F_ALL);
        addChip(R.string.chip_unread, F_UNREAD);
        addChip(R.string.chip_pinned, F_PINNED);
        if (anyBlocked || filter == F_BLOCKED) {
            addChip(R.string.chip_blocked, F_BLOCKED);
        }
    }

    private void addChip(int label, final int f) {
        boolean on = filter == f;
        TextView c = tv(getString(label), 14, on ? look.onAccent : look.text, on);
        c.setPadding(dp(16), dp(7), dp(16), dp(7));
        c.setBackground(Ui.rect(on ? look.accent : look.surface2, dp(18)));
        LinearLayout.LayoutParams p = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(4), 0, dp(4), 0);
        c.setLayoutParams(p);
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filter = f;
                rebuildChips();
                load();
            }
        });
        chipRow.addView(c);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        banner.setVisibility(SmsUtil.isDefault(this) ? View.GONE : View.VISIBLE);
        if (hasPermissions()) {
            if (!observing) {
                getContentResolver().registerContentObserver(
                    Telephony.Sms.CONTENT_URI, true, observer);
                observing = true;
            }
            load();
        } else {
            showEmpty();
            if (Build.VERSION.SDK_INT >= 23 && !askedPerms) {
                askedPerms = true;
                requestPermissions(PERMS, REQ_PERMS);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (observing) {
            getContentResolver().unregisterContentObserver(observer);
            observing = false;
        }
    }

    @Override
    public void onBackPressed() {
        if (selected.size() > 0) {
            clearSelection();
        } else if (searchBar.getVisibility() == View.VISIBLE) {
            closeSearch();
        } else {
            super.onBackPressed();
        }
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        for (int i = 0; i < PERMS.length; i++) {
            if (checkSelfPermission(PERMS[i]) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestDefault() {
        Intent i = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
        i.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
        startActivity(i);
    }

    // ---------------------------------------------------------------- search

    private void openSearch() {
        titleBar.setVisibility(View.GONE);
        searchBar.setVisibility(View.VISIBLE);
        Ui.showKeyboard(this, searchInput);
    }

    private void closeSearch() {
        searchInput.setText("");
        query = "";
        Ui.hideKeyboard(this);
        searchBar.setVisibility(View.GONE);
        titleBar.setVisibility(View.VISIBLE);
        load();
    }

    // ---------------------------------------------------------------- menu

    private void showMainMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, R.string.menu_chat_style);
        m.add(0, 2, 1, R.string.menu_starred);
        m.add(0, 3, 2, R.string.menu_insights);
        m.add(0, 4, 3, R.string.menu_settings);
        if (SmsUtil.isDefault(this)) {
            m.add(0, 5, 4, R.string.menu_mark_all_read);
        } else {
            m.add(0, 6, 4, R.string.set_default);
        }
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        startActivity(new Intent(MainActivity.this, StyleActivity.class));
                        return true;
                    case 2:
                        startActivity(new Intent(MainActivity.this, StarredActivity.class));
                        return true;
                    case 3:
                        startActivity(new Intent(MainActivity.this, StatsActivity.class));
                        return true;
                    case 4:
                        startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                        return true;
                    case 5:
                        markAllRead();
                        return true;
                    case 6:
                        requestDefault();
                        return true;
                }
                return false;
            }
        });
        pm.show();
    }

    private void markAllRead() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    ContentValues v = new ContentValues();
                    v.put("read", 1);
                    v.put("seen", 1);
                    getContentResolver().update(Telephony.Sms.CONTENT_URI, v, "read = 0", null);
                } catch (Exception e) {
                    // ignore
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void r) {
                Prefs.putString(MainActivity.this, "manual_unread", "");
                load();
            }
        }.execute();
    }

    // ---------------------------------------------------------------- selection

    private void onRowClick(Conversation cv) {
        if (cv.kind == 1) {
            return;
        }
        if (selected.size() > 0 && cv.kind == 0) {
            toggleSelect(cv);
            return;
        }
        Intent i = new Intent(this, ThreadActivity.class);
        i.putExtra("address", cv.address);
        if (cv.kind == 2) {
            i.putExtra("jump_id", cv.msgId);
        }
        startActivity(i);
    }

    private void toggleSelect(Conversation cv) {
        String k = Prefs.key(cv.address);
        if (selected.contains(k)) {
            selected.remove(k);
        } else {
            selected.add(k);
        }
        updateSelectionUi();
    }

    private void clearSelection() {
        selected.clear();
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        boolean on = selected.size() > 0;
        selBar.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) {
            selCount.setText(String.valueOf(selected.size()));
            Look.window(this, look, look.surface);
        } else {
            Look.window(this, look, look.bg);
        }
        adapter.notifyDataSetChanged();
    }

    private ArrayList<Conversation> selectedConvs() {
        ArrayList<Conversation> out = new ArrayList<Conversation>();
        for (int i = 0; i < items.size(); i++) {
            Conversation cv = items.get(i);
            if (cv.kind == 0 && selected.contains(Prefs.key(cv.address))) {
                out.add(cv);
            }
        }
        return out;
    }

    private void runSelectionAction(int action) {
        final ArrayList<Conversation> sel = selectedConvs();
        if (sel.size() == 0) {
            return;
        }
        int i;
        switch (action) {
            case 1: { // pin / unpin
                boolean allPinned = true;
                for (i = 0; i < sel.size(); i++) {
                    if (!sel.get(i).pinned) {
                        allPinned = false;
                    }
                }
                for (i = 0; i < sel.size(); i++) {
                    Prefs.setPinned(this, sel.get(i).address, !allPinned);
                }
                break;
            }
            case 2: { // read / unread
                boolean anyUnread = false;
                for (i = 0; i < sel.size(); i++) {
                    if (sel.get(i).unread > 0) {
                        anyUnread = true;
                    }
                }
                for (i = 0; i < sel.size(); i++) {
                    Conversation cv = sel.get(i);
                    if (anyUnread) {
                        Prefs.setMarkedUnread(this, cv.address, false);
                        SmsUtil.markThreadRead(this, cv.threadId);
                        Notifier.cancel(this, cv.address);
                    } else {
                        Prefs.setMarkedUnread(this, cv.address, true);
                    }
                }
                break;
            }
            case 3: { // mute / unmute
                boolean allMuted = true;
                for (i = 0; i < sel.size(); i++) {
                    if (!sel.get(i).muted) {
                        allMuted = false;
                    }
                }
                for (i = 0; i < sel.size(); i++) {
                    Prefs.setMuted(this, sel.get(i).address, !allMuted);
                }
                break;
            }
            case 4: { // block / unblock
                boolean allBlocked = true;
                for (i = 0; i < sel.size(); i++) {
                    if (!sel.get(i).blocked) {
                        allBlocked = false;
                    }
                }
                for (i = 0; i < sel.size(); i++) {
                    Prefs.setBlocked(this, sel.get(i).address, !allBlocked);
                }
                Ui.toast(this, allBlocked ? R.string.unblocked : R.string.blocked_toast);
                break;
            }
            case 5: { // delete
                if (!SmsUtil.isDefault(this)) {
                    Ui.toast(this, R.string.need_default);
                    return;
                }
                new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.delete_convs_confirm, sel.size()))
                    .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            for (int j = 0; j < sel.size(); j++) {
                                SmsUtil.deleteThread(MainActivity.this, sel.get(j).address);
                            }
                            clearSelection();
                            load();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
                return;
            }
        }
        clearSelection();
        load();
    }

    // ---------------------------------------------------------------- loading

    private void showEmpty() {
        if (items.size() > 0) {
            empty.setVisibility(View.GONE);
            return;
        }
        empty.setVisibility(View.VISIBLE);
        if (!hasPermissions()) {
            empty.setText(R.string.perm_needed);
        } else if (query.length() > 0) {
            empty.setText(R.string.no_results);
        } else if (filter == F_UNREAD) {
            empty.setText(R.string.no_unread);
        } else if (filter == F_PINNED) {
            empty.setText(R.string.no_pinned);
        } else if (filter == F_BLOCKED) {
            empty.setText(R.string.no_blocked);
        } else {
            empty.setText(R.string.no_conversations);
        }
    }

    /** Loads off the UI thread; coalesces rapid change events. */
    private void load() {
        if (loading) {
            pending = true;
            return;
        }
        loading = true;
        final String q = query;
        final int flt = filter;
        new AsyncTask<Void, Void, Result>() {
            @Override
            protected Result doInBackground(Void... params) {
                return buildResult(q, flt);
            }

            @Override
            protected void onPostExecute(Result result) {
                items.clear();
                items.addAll(result.list);
                boolean chipsChanged = anyBlocked != result.anyBlocked;
                anyBlocked = result.anyBlocked;
                if (chipsChanged) {
                    rebuildChips();
                }
                adapter.notifyDataSetChanged();
                showEmpty();
                loading = false;
                if (pending) {
                    pending = false;
                    load();
                }
            }
        }.execute();
    }

    private Result buildResult(String q, int flt) {
        Result res = new Result();
        LinkedHashMap<Long, Conversation> map = new LinkedHashMap<Long, Conversation>();
        Cursor c = null;
        try {
            c = SmsUtil.querySms(this,
                new String[] { "thread_id", "address", "body", "date", "read", "type" },
                null, null, "date DESC");
            boolean hasSub = c != null && c.getColumnCount() > 6;
            while (c != null && c.moveToNext()) {
                long tid = c.getLong(0);
                Conversation cv = map.get(Long.valueOf(tid));
                if (cv == null) {
                    String addr = c.getString(1);
                    if (addr == null) {
                        continue;
                    }
                    cv = new Conversation();
                    cv.threadId = tid;
                    cv.address = addr;
                    cv.snippet = c.getString(2);
                    cv.date = c.getLong(3);
                    cv.lastType = c.getInt(5);
                    if (hasSub && !c.isNull(6)) {
                        cv.subId = c.getInt(6);
                    }
                    map.put(Long.valueOf(tid), cv);
                }
                if (c.getInt(4) == 0 && c.getInt(5) == 1) {
                    cv.unread++;
                }
            }
        } catch (Exception e) {
            // permission missing or provider error: show empty list
        } finally {
            if (c != null) {
                c.close();
            }
        }

        String ql = q.toLowerCase(Locale.getDefault());
        ArrayList<Conversation> pinned = new ArrayList<Conversation>();
        ArrayList<Conversation> rest = new ArrayList<Conversation>();
        for (Conversation cv : map.values()) {
            cv.info = Contacts.lookup(this, cv.address);
            cv.name = cv.info.name;
            cv.pinned = Prefs.isPinned(this, cv.address);
            cv.muted = Prefs.isMuted(this, cv.address);
            cv.blocked = Prefs.isBlocked(this, cv.address);
            cv.draft = Prefs.draft(this, cv.address);
            if (cv.blocked) {
                res.anyBlocked = true;
            }
            if (Prefs.isMarkedUnread(this, cv.address) && cv.unread == 0) {
                cv.unread = 1;
            }
            if (flt == F_BLOCKED) {
                if (!cv.blocked) {
                    continue;
                }
            } else if (cv.blocked) {
                continue;
            }
            if (flt == F_UNREAD && cv.unread == 0) {
                continue;
            }
            if (flt == F_PINNED && !cv.pinned) {
                continue;
            }
            if (ql.length() > 0) {
                String hay = ((cv.name == null ? "" : cv.name) + " " + cv.address + " "
                    + (cv.snippet == null ? "" : cv.snippet)).toLowerCase(Locale.getDefault());
                if (!hay.contains(ql)) {
                    continue;
                }
            }
            if (cv.pinned && ql.length() == 0) {
                pinned.add(cv);
            } else {
                rest.add(cv);
            }
        }

        if (ql.length() > 0 && (pinned.size() + rest.size()) > 0) {
            res.list.add(header(getString(R.string.section_conversations)));
        }
        res.list.addAll(pinned);
        res.list.addAll(rest);

        if (q.length() >= 2) {
            ArrayList<Conversation> hits = new ArrayList<Conversation>();
            Cursor m = null;
            try {
                String escapedQuery = q.replace("\\", "\\\\")
                    .replace("%", "\\%").replace("_", "\\_");
                m = getContentResolver().query(Telephony.Sms.CONTENT_URI,
                    new String[] { "_id", "address", "body", "date", "type" },
                    "body LIKE ? ESCAPE '\\'", new String[] { "%" + escapedQuery + "%" }, "date DESC LIMIT 40");
                while (m != null && m.moveToNext()) {
                    String addr = m.getString(1);
                    if (addr == null) {
                        continue;
                    }
                    Conversation h = new Conversation();
                    h.kind = 2;
                    h.msgId = m.getLong(0);
                    h.address = addr;
                    h.snippet = m.getString(2);
                    h.date = m.getLong(3);
                    h.lastType = m.getInt(4);
                    h.info = Contacts.lookup(this, addr);
                    h.name = h.info.name;
                    hits.add(h);
                }
            } catch (Exception e) {
                // ignore
            } finally {
                if (m != null) {
                    m.close();
                }
            }
            if (hits.size() > 0) {
                res.list.add(header(getString(R.string.section_messages)));
                res.list.addAll(hits);
            }
        }
        return res;
    }

    private Conversation header(String text) {
        Conversation h = new Conversation();
        h.kind = 1;
        h.headerText = text;
        return h;
    }

    // ---------------------------------------------------------------- adapter

    private View buildRow() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(16), dp(10), dp(16), dp(10));
        root.setLayoutParams(new ListView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Holder h = new Holder();
        h.avatar = new ImageView(this);
        h.avatar.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(h.avatar, lp(dp(52), dp(52)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = weight(1f);
        cp.setMargins(dp(14), 0, 0, 0);
        root.addView(col, cp);

        LinearLayout l1 = new LinearLayout(this);
        l1.setOrientation(LinearLayout.HORIZONTAL);
        l1.setGravity(Gravity.CENTER_VERTICAL);
        h.name = oneLine("", 16, look.text, false);
        l1.addView(h.name, weight(1f));
        h.time = tv("", 12, look.text2, false);
        h.time.setPadding(dp(8), 0, 0, 0);
        l1.addView(h.time);
        col.addView(l1);

        LinearLayout l2 = new LinearLayout(this);
        l2.setOrientation(LinearLayout.HORIZONTAL);
        l2.setGravity(Gravity.CENTER_VERTICAL);
        l2.setPadding(0, dp(2), 0, 0);
        h.snippet = oneLine("", 14, look.text2, false);
        l2.addView(h.snippet, weight(1f));
        h.sim = tv("", 10, 0xFFFFFFFF, true);
        h.sim.setGravity(Gravity.CENTER);
        h.sim.setPadding(dp(6), dp(1), dp(6), dp(1));
        LinearLayout.LayoutParams simp = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        simp.setMargins(dp(6), 0, 0, 0);
        l2.addView(h.sim, simp);
        h.mute = new ImageView(this);
        Ui.icon(h.mute, R.drawable.ic_bell_off, look.text2);
        LinearLayout.LayoutParams ip = lp(dp(16), dp(16));
        ip.setMargins(dp(6), 0, 0, 0);
        l2.addView(h.mute, ip);
        h.pin = new ImageView(this);
        Ui.icon(h.pin, R.drawable.ic_pin, look.text2);
        l2.addView(h.pin, ip);
        h.badge = tv("", 12, look.onAccent, true);
        h.badge.setGravity(Gravity.CENTER);
        h.badge.setMinWidth(dp(22));
        h.badge.setPadding(dp(6), dp(1), dp(6), dp(1));
        LinearLayout.LayoutParams bp = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(dp(8), 0, 0, 0);
        l2.addView(h.badge, bp);
        col.addView(l2);

        root.setTag(h);
        return root;
    }

    private Drawable checkAvatar() {
        Drawable circle = Ui.oval(look.accent);
        Drawable check = Ui.tinted(this, R.drawable.ic_check, look.onAccent);
        InsetDrawable in = new InsetDrawable(check, dp(13));
        return new LayerDrawable(new Drawable[] { circle, in });
    }

    private CharSequence highlight(String text, String q) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\n', ' ');
        int idx = t.toLowerCase(Locale.getDefault()).indexOf(q.toLowerCase(Locale.getDefault()));
        if (idx < 0) {
            return t;
        }
        int cut = 0;
        if (idx > 18) {
            cut = idx - 14;
            t = "\u2026" + t.substring(cut);
            idx = idx - cut + 1;
        }
        SpannableString sp = new SpannableString(t);
        int end = Math.min(t.length(), idx + q.length());
        sp.setSpan(new BackgroundColorSpan(Look.alpha(look.accent, 0x55)), idx, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sp.setSpan(new StyleSpan(Typeface.BOLD), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    private class ConversationAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).kind == 1 ? 1 : 0;
        }

        @Override
        public boolean isEnabled(int position) {
            return items.get(position).kind != 1;
        }

        @Override
        public View getView(int position, View v, ViewGroup parent) {
            Conversation c = items.get(position);
            if (c.kind == 1) {
                TextView t;
                if (v == null) {
                    t = tv("", 13, look.accent, true);
                    t.setPadding(dp(18), dp(14), dp(18), dp(4));
                } else {
                    t = (TextView) v;
                }
                t.setText(c.headerText);
                return t;
            }
            if (v == null) {
                v = buildRow();
            }
            Holder h = (Holder) v.getTag();
            String label = (c.name != null && c.name.length() > 0) ? c.name : c.address;
            boolean sel = c.kind == 0 && selected.contains(Prefs.key(c.address));

            if (sel) {
                h.avatar.setImageDrawable(checkAvatar());
                v.setBackground(new ColorDrawable(Look.alpha(look.accent, 0x24)));
            } else {
                h.avatar.setImageDrawable(Contacts.avatar(c.address, c.info));
                v.setBackground(Ui.ripple(Look.alpha(look.text, 0x14), null, 0f));
            }
            h.name.setText(label);
            h.name.setTypeface(null, c.unread > 0 ? Typeface.BOLD : Typeface.NORMAL);
            h.time.setText(SmsUtil.formatTime(MainActivity.this, c.date));
            h.time.setTextColor(c.unread > 0 ? look.accent : look.text2);

            CharSequence snip;
            if (c.kind == 2) {
                snip = highlight(c.snippet, query);
            } else if (c.draft != null && c.draft.length() > 0) {
                String pre = getString(R.string.draft_prefix) + " ";
                SpannableString sp = new SpannableString(pre + c.draft.replace('\n', ' '));
                sp.setSpan(new ForegroundColorSpan(look.danger), 0, pre.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                snip = sp;
            } else {
                String s = c.snippet == null ? "" : c.snippet.replace('\n', ' ');
                snip = (c.lastType != 1 ? getString(R.string.you_prefix) + " " : "") + s;
            }
            h.snippet.setText(snip);
            h.snippet.setTextColor(c.unread > 0 ? look.text : look.text2);

            int slotN = c.kind == 0 ? Sims.slotNumber(MainActivity.this, c.subId) : 0;
            if (slotN > 0) {
                h.sim.setText(getString(R.string.sim_n, slotN));
                h.sim.setBackground(Ui.rect(Sims.colorOf(MainActivity.this, c.subId), dp(8)));
                h.sim.setVisibility(View.VISIBLE);
            } else {
                h.sim.setVisibility(View.GONE);
            }
            if (c.kind == 0) {
                h.mute.setVisibility(c.muted ? View.VISIBLE : View.GONE);
                h.pin.setVisibility(c.pinned ? View.VISIBLE : View.GONE);
                if (c.unread > 0) {
                    h.badge.setText(String.valueOf(c.unread));
                    h.badge.setBackground(Ui.rect(c.muted ? look.text2 : look.accent, dp(11)));
                    h.badge.setTextColor(c.muted ? look.bg : look.onAccent);
                    h.badge.setVisibility(View.VISIBLE);
                } else {
                    h.badge.setVisibility(View.GONE);
                }
            } else {
                h.mute.setVisibility(View.GONE);
                h.pin.setVisibility(View.GONE);
                h.badge.setVisibility(View.GONE);
            }
            return v;
        }
    }
}
