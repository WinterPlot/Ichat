package com.userlabs.ichat;

import androidx.appcompat.app.AlertDialog;
import android.app.DatePickerDialog;
import android.Manifest;
import android.app.TimePickerDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.TimeZone;

/** One conversation: styled bubbles, search, multi-select, emoji, send later, delivery status. */
public class ThreadActivity extends BaseActivity {

    static class Msg {
        long id;
        String body;
        long date;
        int type;
        int status;
        boolean out;
        long dayKey;
        boolean showDate;
        boolean joinPrev;
        boolean joinNext;
        String otp;
        int subId = -1;
    }

    static class Holder {
        TextView dateHeader;
        LinearLayout row;
        ImageView avatar;
        LinearLayout col;
        LinkTextView bubble;
        TextView chip;
        TextView meta;
    }

    private static final int PAGE = 300;
    private static final long GROUP_GAP = 5L * 60L * 1000L;

    private String address;
    private long threadId = -2; // -2 = not resolved yet, -1 = unavailable
    private int limit = PAGE;
    private boolean hasMore;
    private boolean firstLoad = true;
    private boolean stickToBottom;
    private boolean keepPosition;
    private long keepId = -1;
    private int keepTop;
    private long jumpId = -1;
    private long flashId = -1;
    private int newWhileAway;
    private Contacts.Info info;

    private ListView listView;
    private EditText input;
    private MsgAdapter adapter;
    private final ArrayList<Msg> items = new ArrayList<Msg>();
    private final HashSet<Long> selected = new HashSet<Long>();
    private final HashSet<Long> expanded = new HashSet<Long>();
    private final HashSet<Long> starredCache = new HashSet<Long>();

    private LinearLayout normalBar;
    private LinearLayout searchBar;
    private LinearLayout selBar;
    private TextView titleView;
    private TextView subView;
    private ImageView headerAvatar;
    private TextView selCount;
    private EditText searchInput;
    private TextView searchCount;
    private FrameLayout fab;
    private TextView fabBadge;
    private LinearLayout suggestRow;
    private HorizontalScrollView suggestScroll;
    private TextView counter;
    private LinearLayout emojiPanel;
    private GridView emojiGrid;
    private LinearLayout emojiTabs;
    private TextView defaultBanner;
    private ImageView sendBtn;
    private TextView simChip;
    private int currentSim = -1;
    private static final int REQ_SIM = 77;

    private String searchQuery = "";
    private final ArrayList<Integer> matches = new ArrayList<Integer>();
    private int matchPos = -1;

    private int styleId;
    private int roundness;
    private int textSizeSp;
    private boolean group;
    private boolean avatars;
    private boolean timeAlways;
    private boolean loading;
    private boolean pending;
    private boolean observing;
    private boolean shownDefaultHint;
    private int emojiCat = 0;
    private final ArrayList<String> emojiShown = new ArrayList<String>();
    private final Handler handler = new Handler();

    private final ContentObserver observer = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            load();
        }
    };

    @Override
    protected String lookAddress() {
        Intent in = getIntent();
        String a = in.getStringExtra("address");
        if (a == null && in.getData() != null) {
            a = SmsUtil.addressFromUri(in.getData());
        }
        return a;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent in = getIntent();
        address = lookAddress();
        String prefill = null;
        if (address != null && in.getStringExtra("address") == null) {
            prefill = in.getStringExtra("sms_body");
            if (prefill == null) {
                prefill = in.getStringExtra(Intent.EXTRA_TEXT);
            }
        }
        // Internal navigation extras are not trusted on exported entry points.
        // External callers may prefill only via the standard SMS body extra.
        if (in.getStringExtra("address") == null && in.getData() != null) {
            String externalBody = in.getStringExtra("sms_body");
            if (externalBody == null) externalBody = in.getStringExtra(Intent.EXTRA_TEXT);
            if (externalBody != null) prefill = externalBody;
        }
        jumpId = in.getStringExtra("address") == null ? -1 : in.getLongExtra("jump_id", -1);
        if (address == null || address.length() == 0) {
            finish();
            return;
        }

        styleId = Prefs.bubbleStyle(this, address);
        roundness = Prefs.roundness(this, address);
        textSizeSp = Prefs.textSize(this);
        group = Prefs.groupMessages(this);
        avatars = Prefs.showAvatars(this);
        timeAlways = Prefs.timeAlways(this);

        ArrayList<Long> st = Prefs.starredIds(this);
        for (int i = 0; i < st.size(); i++) {
            starredCache.add(st.get(i));
        }

        info = Contacts.cached(address);
        setContentView(buildUi());
        Look.window(this, look, look.surface);
        bindHeader();

        String draft = Prefs.draft(this, address);
        if (prefill != null) {
            input.setText(prefill);
            input.setSelection(prefill.length());
        } else if (draft != null && draft.length() > 0) {
            input.setText(draft);
            input.setSelection(draft.length());
        }
        onInputChanged(input.getText().toString());
    }

    // ---------------------------------------------------------------- UI construction

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(look.bg);

        FrameLayout headerHost = new FrameLayout(this);
        headerHost.setBackgroundColor(look.surface);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            headerHost.setElevation(dp(3));
        }

        // normal header
        normalBar = headerBar();
        ImageView back = iconBtn(R.drawable.ic_back, look.text);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
        normalBar.addView(back);
        headerAvatar = new ImageView(this);
        headerAvatar.setScaleType(ImageView.ScaleType.FIT_XY);
        LinearLayout.LayoutParams ap = lp(dp(38), dp(38));
        ap.setMargins(dp(2), 0, dp(10), 0);
        normalBar.addView(headerAvatar, ap);
        LinearLayout nameCol = new LinearLayout(this);
        nameCol.setOrientation(LinearLayout.VERTICAL);
        titleView = oneLine("", 17, look.text, true);
        subView = oneLine("", 12, look.text2, false);
        nameCol.addView(titleView);
        nameCol.addView(subView);
        normalBar.addView(nameCol, weight(1f));
        ImageView call = iconBtn(R.drawable.ic_call, look.text);
        call.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", address, null)));
            }
        });
        normalBar.addView(call);
        ImageView search = iconBtn(R.drawable.ic_search, look.text);
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSearch();
            }
        });
        normalBar.addView(search);
        final ImageView more = iconBtn(R.drawable.ic_more, look.text);
        more.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(more);
            }
        });
        normalBar.addView(more);
        headerHost.addView(normalBar);

        // search header
        searchBar = headerBar();
        searchBar.setBackgroundColor(look.surface);
        searchBar.setVisibility(View.GONE);
        ImageView sClose = iconBtn(R.drawable.ic_back, look.text);
        sClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSearch();
            }
        });
        searchBar.addView(sClose);
        searchInput = new EditText(this);
        searchInput.setHint(R.string.search_in_chat);
        searchInput.setHintTextColor(look.text2);
        searchInput.setTextColor(look.text);
        searchInput.setSingleLine(true);
        searchInput.setBackgroundColor(0);
        searchInput.setTextSize(16);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim();
                runSearch(true);
            }
        });
        searchBar.addView(searchInput, weight(1f));
        searchCount = tv("", 13, look.text2, false);
        searchBar.addView(searchCount);
        ImageView up = iconBtn(R.drawable.ic_up, look.text);
        up.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepMatch(-1);
            }
        });
        searchBar.addView(up);
        ImageView down = iconBtn(R.drawable.ic_down, look.text);
        down.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepMatch(1);
            }
        });
        searchBar.addView(down);
        headerHost.addView(searchBar);

        // selection header
        selBar = headerBar();
        selBar.setBackgroundColor(look.surface);
        selBar.setVisibility(View.GONE);
        ImageView selClose = iconBtn(R.drawable.ic_close, look.text);
        selClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearSelection();
            }
        });
        selBar.addView(selClose);
        selCount = tv("", 18, look.text, true);
        selCount.setPadding(dp(4), 0, 0, 0);
        selBar.addView(selCount, weight(1f));
        selBar.addView(selAction(R.drawable.ic_copy, 1));
        selBar.addView(selAction(R.drawable.ic_forward, 2));
        selBar.addView(selAction(R.drawable.ic_star, 3));
        selBar.addView(selAction(R.drawable.ic_share, 4));
        selBar.addView(selAction(R.drawable.ic_info, 5));
        selBar.addView(selAction(R.drawable.ic_delete, 6));
        headerHost.addView(selBar);
        root.addView(headerHost, lp(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        // content: wallpaper + list + overlays
        FrameLayout content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        content.setBackground(new WallpaperDrawable(Prefs.wallpaper(this, address), look, dens));

        listView = new ListView(this);
        listView.setDivider(null);
        listView.setDividerHeight(0);
        listView.setCacheColorHint(0);
        listView.setSelector(new ColorDrawable(0));
        listView.setStackFromBottom(true);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(6), 0, dp(8));
        adapter = new MsgAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                onRowClick(pos);
            }
        });
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
                Msg m = msgAt(pos);
                if (m == null) {
                    return false;
                }
                Ui.tick(view);
                toggleSelect(m);
                return true;
            }
        });
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int first, int visible, int total) {
                updateFab();
            }
        });
        content.addView(listView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        defaultBanner = tv(getString(R.string.thread_default_hint), 13, look.text, false);
        defaultBanner.setGravity(Gravity.CENTER);
        defaultBanner.setPadding(dp(14), dp(8), dp(14), dp(8));
        defaultBanner.setBackgroundColor(Look.alpha(look.accent, 0xDD));
        defaultBanner.setTextColor(look.onAccent);
        defaultBanner.setVisibility(View.GONE);
        defaultBanner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
                i.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
                startActivity(i);
            }
        });
        content.addView(defaultBanner, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP));

        fab = new FrameLayout(this);
        ImageView fabIcon = new ImageView(this);
        fabIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        fabIcon.setPadding(dp(9), dp(9), dp(9), dp(9));
        Ui.icon(fabIcon, R.drawable.ic_down, look.text);
        fab.setBackground(Ui.oval(look.surface));
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            fab.setElevation(dp(4));
        }
        fab.addView(fabIcon, new FrameLayout.LayoutParams(dp(40), dp(40)));
        fabBadge = tv("", 11, look.onAccent, true);
        fabBadge.setGravity(Gravity.CENTER);
        fabBadge.setMinWidth(dp(18));
        fabBadge.setPadding(dp(4), 0, dp(4), 0);
        fabBadge.setBackground(Ui.rect(look.accent, dp(9)));
        fabBadge.setVisibility(View.GONE);
        FrameLayout.LayoutParams bpp = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, dp(18), Gravity.TOP | Gravity.RIGHT);
        fab.addView(fabBadge, bpp);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(40), dp(40),
            Gravity.BOTTOM | Gravity.RIGHT);
        fp.setMargins(0, 0, dp(14), dp(10));
        fab.setVisibility(View.GONE);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scrollToBottom();
            }
        });
        content.addView(fab, fp);
        root.addView(content);

        // smart replies
        suggestScroll = new HorizontalScrollView(this);
        suggestScroll.setHorizontalScrollBarEnabled(false);
        suggestScroll.setBackgroundColor(look.surface);
        suggestRow = new LinearLayout(this);
        suggestRow.setOrientation(LinearLayout.HORIZONTAL);
        suggestRow.setPadding(dp(8), dp(6), dp(8), 0);
        suggestScroll.addView(suggestRow);
        suggestScroll.setVisibility(View.GONE);
        root.addView(suggestScroll);

        // input bar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(look.surface);
        counter = tv("", 11, look.text2, false);
        counter.setGravity(Gravity.RIGHT);
        counter.setPadding(0, dp(2), dp(70), 0);
        counter.setVisibility(View.GONE);
        bar.addView(counter, lp(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.BOTTOM);
        inputRow.setPadding(dp(4), dp(4), dp(8), dp(6));

        ImageView emojiBtn = iconBtn(R.drawable.ic_emoji, look.text2);
        emojiBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleEmoji();
            }
        });
        inputRow.addView(emojiBtn);

        input = new EditText(this);
        input.setHint(R.string.message_hint);
        input.setHintTextColor(look.text2);
        input.setTextColor(look.text);
        input.setTextSize(16);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(5);
        input.setBackground(Ui.rect(look.surface2, dp(22)));
        input.setPadding(dp(16), dp(9), dp(16), dp(9));
        LinearLayout.LayoutParams ip = weight(1f);
        ip.setMargins(0, 0, dp(4), dp(2));
        inputRow.addView(input, ip);
        input.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideEmoji();
            }
        });
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    hideEmoji();
                }
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onInputChanged(s.toString());
            }
        });

        ImageView later = iconBtn(R.drawable.ic_schedule_send, look.text2);
        later.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scheduleDialog();
            }
        });
        inputRow.addView(later);

        simChip = tv("", 11, 0xFFFFFFFF, true);
        simChip.setGravity(Gravity.CENTER);
        simChip.setPadding(dp(8), dp(3), dp(8), dp(3));
        simChip.setVisibility(View.GONE);
        simChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickSim();
            }
        });
        LinearLayout.LayoutParams scp = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        scp.gravity = Gravity.CENTER_VERTICAL;
        scp.setMargins(dp(2), 0, dp(2), 0);
        inputRow.addView(simChip, scp);

        sendBtn = new ImageView(this);
        sendBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sendBtn.setPadding(dp(11), dp(11), dp(8), dp(11));
        Ui.icon(sendBtn, R.drawable.ic_send, look.onAccent);
        sendBtn.setBackground(Ui.ripple(0x33FFFFFF, Ui.oval(look.accent), dp(22)));
        LinearLayout.LayoutParams sp = lp(dp(44), dp(44));
        sp.setMargins(dp(2), 0, 0, dp(2));
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });
        sendBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                scheduleDialog();
                return true;
            }
        });
        inputRow.addView(sendBtn, sp);
        bar.addView(inputRow);
        root.addView(bar);

        emojiPanel = new LinearLayout(this);
        emojiPanel.setOrientation(LinearLayout.VERTICAL);
        emojiPanel.setBackgroundColor(look.surface);
        emojiPanel.setVisibility(View.GONE);
        root.addView(emojiPanel, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(260)));
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

    private void bindHeader() {
        String label = info != null && info.name != null && info.name.length() > 0
            ? info.name : address;
        titleView.setText(label);
        boolean named = info != null && info.found;
        subView.setText(named ? address : (Prefs.isBlocked(this, address)
            ? getString(R.string.blocked_label) : ""));
        subView.setVisibility(subView.getText().length() > 0 ? View.VISIBLE : View.GONE);
        headerAvatar.setImageDrawable(Contacts.avatar(address, info));
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        if (address == null) {
            return;
        }
        Notifier.cancel(this, address);
        Prefs.setMarkedUnread(this, address, false);
        if (Build.VERSION.SDK_INT >= 23 && !Sims.hasPermission(this)
            && !Prefs.getBool(this, "sim_asked", false)) {
            Prefs.putBool(this, "sim_asked", true);
            requestPermissions(new String[] { Manifest.permission.READ_PHONE_STATE }, REQ_SIM);
        }
        refreshSim();
        defaultBanner.setVisibility(SmsUtil.isDefault(this) ? View.GONE : View.VISIBLE);
        if (!observing) {
            getContentResolver().registerContentObserver(
                Telephony.Sms.CONTENT_URI, true, observer);
            observing = true;
        }
        load();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (address != null && input != null) {
            Prefs.setDraft(this, address, input.getText().toString());
        }
        if (observing) {
            getContentResolver().unregisterContentObserver(observer);
            observing = false;
        }
    }

    @Override
    public void onBackPressed() {
        if (emojiPanel != null && emojiPanel.getVisibility() == View.VISIBLE) {
            hideEmoji();
        } else if (selected.size() > 0) {
            clearSelection();
        } else if (searchBar != null && searchBar.getVisibility() == View.VISIBLE) {
            closeSearch();
        } else {
            super.onBackPressed();
        }
    }

    // ---------------------------------------------------------------- loading

    private Msg msgAt(int position) {
        int off = hasMore ? 1 : 0;
        int idx = position - off;
        if (idx < 0 || idx >= items.size()) {
            return null;
        }
        return items.get(idx);
    }

    private void load() {
        if (loading) {
            pending = true;
            return;
        }
        loading = true;
        final int lim = limit;
        new AsyncTask<Void, Void, ArrayList<Msg>>() {
            @Override
            protected ArrayList<Msg> doInBackground(Void... params) {
                ArrayList<Msg> out = new ArrayList<Msg>();
                boolean hadUnread = false;
                if (info == null || !info.found) {
                    info = Contacts.lookup(ThreadActivity.this, address);
                }
                if (threadId == -2) {
                    threadId = SmsUtil.threadIdFor(ThreadActivity.this, address);
                }
                String sel;
                String[] args;
                if (threadId > 0) {
                    sel = "thread_id = ?";
                    args = new String[] { String.valueOf(threadId) };
                } else {
                    sel = "address = ?";
                    args = new String[] { address };
                }
                Cursor c = null;
                try {
                    c = SmsUtil.querySms(ThreadActivity.this,
                        new String[] { "_id", "body", "date", "type", "read", "status" },
                        sel, args, "date DESC LIMIT " + (lim + 1));
                    boolean hasSub = c != null && c.getColumnCount() > 6;
                    while (c != null && c.moveToNext()) {
                        Msg m = new Msg();
                        m.id = c.getLong(0);
                        m.body = c.getString(1);
                        if (m.body == null) {
                            m.body = "";
                        }
                        m.date = c.getLong(2);
                        m.type = c.getInt(3);
                        m.out = m.type != 1;
                        m.status = c.getInt(5);
                        if (hasSub && !c.isNull(6)) {
                            m.subId = c.getInt(6);
                        }
                        if (m.type == 1 && c.getInt(4) == 0) {
                            hadUnread = true;
                        }
                        out.add(m);
                    }
                } catch (Exception e) {
                    // ignore: empty thread
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
                if (hadUnread && threadId > 0 && SmsUtil.isDefault(ThreadActivity.this)) {
                    SmsUtil.markThreadRead(ThreadActivity.this, threadId);
                }
                return out;
            }

            @Override
            protected void onPostExecute(ArrayList<Msg> newestFirst) {
                boolean more = newestFirst.size() > lim;
                if (more) {
                    newestFirst.remove(newestFirst.size() - 1);
                }
                Collections.reverse(newestFirst);
                applyResult(newestFirst, more);
                loading = false;
                if (pending) {
                    pending = false;
                    load();
                }
            }
        }.execute();
    }

    private void applyResult(ArrayList<Msg> list, boolean more) {
        boolean wasAtBottom = isAtBottom();
        int oldCount = items.size();
        long oldLastId = oldCount > 0 ? items.get(oldCount - 1).id : -1;
        long prevDay = Long.MIN_VALUE;
        for (int i = 0; i < list.size(); i++) {
            Msg m = list.get(i);
            m.dayKey = (m.date + TimeZone.getDefault().getOffset(m.date)) / 86400000L;
            m.showDate = m.dayKey != prevDay;
            prevDay = m.dayKey;
            m.otp = m.out ? null : SmsUtil.otp(m.body);
        }
        for (int i = 0; i < list.size(); i++) {
            Msg m = list.get(i);
            if (group && i > 0) {
                Msg p = list.get(i - 1);
                m.joinPrev = p.out == m.out && p.dayKey == m.dayKey && (m.date - p.date) < GROUP_GAP
                    && !m.showDate;
            }
            if (group && i + 1 < list.size()) {
                Msg n = list.get(i + 1);
                m.joinNext = n.out == m.out && n.dayKey == m.dayKey && (n.date - m.date) < GROUP_GAP;
            }
        }
        items.clear();
        items.addAll(list);
        hasMore = more;

        long newLastId = items.size() > 0 ? items.get(items.size() - 1).id : -1;
        boolean grew = !firstLoad && newLastId != oldLastId && items.size() >= oldCount;
        adapter.notifyDataSetChanged();

        if (firstLoad) {
            firstLoad = false;
            if (jumpId > 0) {
                jumpTo(jumpId);
            } else {
                scrollToBottomNow();
            }
        } else if (keepPosition) {
            keepPosition = false;
            int idx = indexOfId(keepId);
            if (idx >= 0) {
                listView.setSelectionFromTop(idx + (hasMore ? 1 : 0), keepTop);
            }
        } else if (jumpId > 0) {
            jumpTo(jumpId);
        } else if (stickToBottom || (grew && wasAtBottom)) {
            stickToBottom = false;
            scrollToBottomNow();
        } else if (grew) {
            Msg last = items.get(items.size() - 1);
            if (!last.out) {
                newWhileAway++;
            }
        }
        updateFab();
        updateSuggestions();
        if (searchQuery.length() > 0) {
            runSearch(false);
        }
    }

    private int indexOfId(long id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    private void jumpTo(long id) {
        int idx = indexOfId(id);
        jumpId = -1;
        if (idx < 0) {
            scrollToBottomNow();
            return;
        }
        final int pos = idx + (hasMore ? 1 : 0);
        flashId = id;
        listView.setSelection(pos);
        listView.post(new Runnable() {
            @Override
            public void run() {
                listView.setSelectionFromTop(pos, dp(120));
            }
        });
        adapter.notifyDataSetChanged();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                flashId = -1;
                adapter.notifyDataSetChanged();
            }
        }, 1800);
    }

    private boolean isAtBottom() {
        if (items.size() == 0) {
            return true;
        }
        int total = adapter.getCount();
        return listView.getLastVisiblePosition() >= total - 1;
    }

    private void scrollToBottomNow() {
        newWhileAway = 0;
        final int total = adapter.getCount();
        if (total > 0) {
            listView.setSelection(total - 1);
            listView.post(new Runnable() {
                @Override
                public void run() {
                    listView.setSelection(adapter.getCount() - 1);
                }
            });
        }
        updateFab();
    }

    private void scrollToBottom() {
        newWhileAway = 0;
        int total = adapter.getCount();
        if (total > 0) {
            listView.smoothScrollToPosition(total - 1);
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateFab();
            }
        }, 400);
    }

    private void updateFab() {
        if (fab == null) {
            return;
        }
        boolean atBottom = isAtBottom();
        if (atBottom) {
            newWhileAway = 0;
        }
        fab.setVisibility(atBottom ? View.GONE : View.VISIBLE);
        if (newWhileAway > 0) {
            fabBadge.setText(String.valueOf(newWhileAway));
            fabBadge.setVisibility(View.VISIBLE);
        } else {
            fabBadge.setVisibility(View.GONE);
        }
    }

    // ---------------------------------------------------------------- sending

    private void onInputChanged(String s) {
        boolean has = s.trim().length() > 0;
        sendBtn.setAlpha(has ? 1f : 0.55f);
        if (s.length() > 100) {
            int[] li = SmsUtil.lengthInfo(s);
            counter.setText(getString(R.string.sms_counter, li[2], li[0]));
            counter.setVisibility(View.VISIBLE);
        } else {
            counter.setVisibility(View.GONE);
        }
        suggestScroll.setVisibility(has ? View.GONE : (suggestRow.getChildCount() > 0
            ? View.VISIBLE : View.GONE));
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_SIM) {
            Sims.invalidate();
            currentSim = -1;
            refreshSim();
            adapter.notifyDataSetChanged();
        }
    }

    /** Shows the SIM chip (dual-SIM phones only) and keeps currentSim valid. */
    private void refreshSim() {
        if (simChip == null || address == null) {
            return;
        }
        if (!Sims.isDual(this)) {
            currentSim = -1;
            simChip.setVisibility(View.GONE);
            return;
        }
        if (Sims.byId(this, currentSim) == null) {
            currentSim = Sims.preferred(this, address);
        }
        Sims.Sim s = Sims.byId(this, currentSim);
        if (s == null) {
            simChip.setVisibility(View.GONE);
            return;
        }
        simChip.setText(getString(R.string.sim_n, s.displaySlot()));
        simChip.setBackground(Ui.rect(s.color, dp(10)));
        simChip.setVisibility(View.VISIBLE);
    }

    private void pickSim() {
        final ArrayList<Sims.Sim> sims = Sims.list(this);
        if (sims.size() < 2) {
            return;
        }
        CharSequence[] labels = new CharSequence[sims.size()];
        int checked = 0;
        for (int i = 0; i < sims.size(); i++) {
            labels[i] = Sims.label(this, sims.get(i));
            if (sims.get(i).subId == currentSim) {
                checked = i;
            }
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.sim_pick_title)
            .setSingleChoiceItems(labels, checked, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    currentSim = sims.get(which).subId;
                    Prefs.setSim(ThreadActivity.this, address, currentSim);
                    refreshSim();
                    dialog.dismiss();
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void sendMessage() {
        String text = input.getText().toString().trim();
        if (text.length() == 0) {
            return;
        }
        try {
            refreshSim();
            SmsUtil.send(this, address, text, currentSim);
            if (currentSim >= 0) {
                Prefs.setSim(this, address, currentSim);
            }
            Ui.tick(sendBtn);
            input.setText("");
            Prefs.setDraft(this, address, "");
            stickToBottom = true;
            if (!SmsUtil.isDefault(this) && !shownDefaultHint) {
                shownDefaultHint = true;
                Toast.makeText(this, R.string.sent_not_default, Toast.LENGTH_LONG).show();
            }
            load();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.could_not_send, e.getMessage()),
                Toast.LENGTH_LONG).show();
        }
    }

    private void scheduleDialog() {
        final String text = input.getText().toString().trim();
        if (text.length() == 0) {
            Ui.toast(this, R.string.type_first);
            return;
        }
        final Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, 1);
        new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, int year, int month, int day) {
                cal.set(Calendar.YEAR, year);
                cal.set(Calendar.MONTH, month);
                cal.set(Calendar.DAY_OF_MONTH, day);
                new TimePickerDialog(ThreadActivity.this, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker tp, int hour, int minute) {
                        cal.set(Calendar.HOUR_OF_DAY, hour);
                        cal.set(Calendar.MINUTE, minute);
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);
                        long when = cal.getTimeInMillis();
                        if (when < System.currentTimeMillis() + 15000L) {
                            Ui.toast(ThreadActivity.this, R.string.pick_future);
                            return;
                        }
                        Scheduler.add(ThreadActivity.this, address, text, when, currentSim);
                        input.setText("");
                        Prefs.setDraft(ThreadActivity.this, address, "");
                        Toast.makeText(ThreadActivity.this, getString(R.string.scheduled_for,
                            SmsUtil.dayLabel(ThreadActivity.this, when) + " "
                                + SmsUtil.clock(ThreadActivity.this, when)), Toast.LENGTH_LONG).show();
                    }
                }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE),
                    Prefs.time24(ThreadActivity.this)).show();
            }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showScheduled() {
        final ArrayList<Scheduler.Item> list = Scheduler.forAddress(this, address);
        if (list.size() == 0) {
            Ui.toast(this, R.string.no_scheduled);
            return;
        }
        CharSequence[] labels = new CharSequence[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Scheduler.Item it = list.get(i);
            labels[i] = SmsUtil.dayLabel(this, it.when) + " " + SmsUtil.clock(this, it.when)
                + "\n" + it.body;
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.scheduled_title)
            .setItems(labels, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    final Scheduler.Item it = list.get(which);
                    new AlertDialog.Builder(ThreadActivity.this)
                        .setMessage(R.string.cancel_scheduled_q)
                        .setPositiveButton(R.string.yes_cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d2, int w2) {
                                Scheduler.cancel(ThreadActivity.this, it.id);
                            }
                        })
                        .setNegativeButton(R.string.keep, null)
                        .show();
                }
            })
            .setNegativeButton(R.string.close, null)
            .show();
    }

    // ---------------------------------------------------------------- smart replies

    private void updateSuggestions() {
        suggestRow.removeAllViews();
        if (!Prefs.smartReplies(this) || items.size() == 0) {
            suggestScroll.setVisibility(View.GONE);
            return;
        }
        Msg last = items.get(items.size() - 1);
        if (last.out) {
            suggestScroll.setVisibility(View.GONE);
            return;
        }
        String[] s = SmsUtil.suggestions(this, last.body);
        for (int i = 0; i < s.length; i++) {
            final String text = s[i];
            TextView chip = tv(text, 14, look.accent, false);
            chip.setSingleLine(true);
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));
            chip.setBackground(Ui.rectStroke(look.bg, Look.alpha(look.accent, 0x90), dp(1), dp(18)));
            LinearLayout.LayoutParams cp = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.setMargins(dp(3), 0, dp(3), 0);
            chip.setLayoutParams(cp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    input.setText(text);
                    input.setSelection(text.length());
                    Ui.showKeyboard(ThreadActivity.this, input);
                }
            });
            suggestRow.addView(chip);
        }
        boolean typing = input.getText().toString().trim().length() > 0;
        suggestScroll.setVisibility(s.length > 0 && !typing ? View.VISIBLE : View.GONE);
    }

    // ---------------------------------------------------------------- emoji

    private void toggleEmoji() {
        if (emojiPanel.getVisibility() == View.VISIBLE) {
            hideEmoji();
            Ui.showKeyboard(this, input);
        } else {
            Ui.hideKeyboard(this);
            buildEmojiPanel();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    emojiPanel.setVisibility(View.VISIBLE);
                    if (isAtBottom()) {
                        scrollToBottomNow();
                    }
                }
            }, 120);
        }
    }

    private void hideEmoji() {
        if (emojiPanel != null) {
            emojiPanel.setVisibility(View.GONE);
        }
    }

    private void buildEmojiPanel() {
        if (emojiPanel.getChildCount() > 0) {
            selectEmojiCat(emojiCat);
            return;
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        emojiTabs = new LinearLayout(this);
        emojiTabs.setOrientation(LinearLayout.HORIZONTAL);
        emojiTabs.setGravity(Gravity.CENTER_VERTICAL);
        hs.addView(emojiTabs);
        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        tabRow.setGravity(Gravity.CENTER_VERTICAL);
        tabRow.addView(hs, weight(1f));
        ImageView bs = iconBtn(R.drawable.ic_backspace, look.text2);
        bs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                input.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
                input.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL));
            }
        });
        tabRow.addView(bs);
        emojiPanel.addView(tabRow, lp(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)));

        emojiGrid = new GridView(this);
        emojiGrid.setNumColumns(GridView.AUTO_FIT);
        emojiGrid.setColumnWidth(dp(46));
        emojiGrid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        emojiGrid.setSelector(new ColorDrawable(0));
        emojiGrid.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return emojiShown.size();
            }

            @Override
            public Object getItem(int position) {
                return emojiShown.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View v, ViewGroup parent) {
                TextView t;
                if (v == null) {
                    t = new TextView(ThreadActivity.this);
                    t.setTextSize(25);
                    t.setGravity(Gravity.CENTER);
                    t.setLayoutParams(new AbsListView.LayoutParams(dp(46), dp(46)));
                } else {
                    t = (TextView) v;
                }
                t.setText(emojiShown.get(position));
                return t;
            }
        });
        emojiGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                String e = emojiShown.get(pos);
                int s = Math.max(input.getSelectionStart(), 0);
                int en = Math.max(input.getSelectionEnd(), 0);
                input.getText().replace(Math.min(s, en), Math.max(s, en), e);
                Prefs.addRecentEmoji(ThreadActivity.this, e);
            }
        });
        emojiPanel.addView(emojiGrid, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        selectEmojiCat(Prefs.recentEmoji(this).size() > 0 ? -1 : 0);
    }

    private void selectEmojiCat(int cat) {
        emojiCat = cat;
        emojiTabs.removeAllViews();
        ArrayList<String> recent = Prefs.recentEmoji(this);
        if (recent.size() > 0) {
            addEmojiTab(EmojiData.cp(0x1F552), -1, cat == -1);
        }
        for (int i = 0; i < EmojiData.CATS; i++) {
            addEmojiTab(EmojiData.TABS[i], i, cat == i);
        }
        emojiShown.clear();
        if (cat == -1) {
            emojiShown.addAll(recent);
        } else {
            for (int i = 0; i < EmojiData.SETS[cat].length; i++) {
                emojiShown.add(EmojiData.SETS[cat][i]);
            }
        }
        ((BaseAdapter) emojiGrid.getAdapter()).notifyDataSetChanged();
        emojiGrid.setSelection(0);
    }

    private void addEmojiTab(String glyph, final int cat, boolean on) {
        TextView t = tv(glyph, 21, look.text, false);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(4), dp(10), dp(4));
        if (on) {
            t.setBackground(Ui.rect(Look.alpha(look.accent, 0x30), dp(14)));
        }
        t.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectEmojiCat(cat);
            }
        });
        emojiTabs.addView(t);
    }

    // ---------------------------------------------------------------- search

    private void openSearch() {
        normalBar.setVisibility(View.GONE);
        searchBar.setVisibility(View.VISIBLE);
        Ui.showKeyboard(this, searchInput);
    }

    private void closeSearch() {
        searchInput.setText("");
        searchQuery = "";
        matches.clear();
        matchPos = -1;
        Ui.hideKeyboard(this);
        searchBar.setVisibility(View.GONE);
        normalBar.setVisibility(View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void runSearch(boolean jump) {
        matches.clear();
        if (searchQuery.length() > 0) {
            String q = searchQuery.toLowerCase(Locale.getDefault());
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).body.toLowerCase(Locale.getDefault()).contains(q)) {
                    matches.add(Integer.valueOf(i));
                }
            }
        }
        if (matches.size() == 0) {
            matchPos = -1;
            searchCount.setText(searchQuery.length() > 0 ? "0" : "");
        } else {
            if (jump || matchPos < 0 || matchPos >= matches.size()) {
                matchPos = matches.size() - 1;
            }
            searchCount.setText((matchPos + 1) + "/" + matches.size());
            if (jump) {
                showMatch();
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void stepMatch(int dir) {
        if (matches.size() == 0) {
            return;
        }
        matchPos = (matchPos + dir + matches.size()) % matches.size();
        searchCount.setText((matchPos + 1) + "/" + matches.size());
        showMatch();
    }

    private void showMatch() {
        if (matchPos < 0 || matchPos >= matches.size()) {
            return;
        }
        int idx = matches.get(matchPos).intValue();
        final int pos = idx + (hasMore ? 1 : 0);
        listView.setSelectionFromTop(pos, dp(140));
        flashId = items.get(idx).id;
        adapter.notifyDataSetChanged();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                flashId = -1;
                adapter.notifyDataSetChanged();
            }
        }, 1200);
    }

    // ---------------------------------------------------------------- selection

    private void onRowClick(int pos) {
        if (hasMore && pos == 0) {
            Msg first = items.size() > 0 ? items.get(0) : null;
            if (first != null) {
                keepId = first.id;
                View v0 = listView.getChildAt(1);
                keepTop = v0 != null ? v0.getTop() : 0;
                keepPosition = true;
            }
            limit += PAGE;
            load();
            return;
        }
        Msg m = msgAt(pos);
        if (m == null) {
            return;
        }
        if (selected.size() > 0) {
            toggleSelect(m);
            return;
        }
        if (m.type == SmsUtil.TYPE_FAILED) {
            failedDialog(m);
            return;
        }
        Long k = Long.valueOf(m.id);
        if (expanded.contains(k)) {
            expanded.remove(k);
        } else {
            expanded.add(k);
        }
        adapter.notifyDataSetChanged();
    }

    private void failedDialog(final Msg m) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.not_sent_title)
            .setItems(new CharSequence[] { getString(R.string.retry), getString(R.string.delete) },
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            try {
                                int rs = Sims.byId(ThreadActivity.this, m.subId) != null ? m.subId : currentSim;
                                SmsUtil.resend(ThreadActivity.this, m.id, address, m.body, rs);
                                stickToBottom = true;
                            } catch (Exception e) {
                                Toast.makeText(ThreadActivity.this,
                                    getString(R.string.could_not_send, e.getMessage()),
                                    Toast.LENGTH_LONG).show();
                            }
                        } else {
                            if (!SmsUtil.deleteMessage(ThreadActivity.this, m.id)) {
                                Ui.toast(ThreadActivity.this, R.string.need_default);
                            }
                        }
                        load();
                    }
                })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void toggleSelect(Msg m) {
        Long k = Long.valueOf(m.id);
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
        normalBar.setVisibility(on || searchBar.getVisibility() == View.VISIBLE
            ? View.GONE : View.VISIBLE);
        if (on) {
            selCount.setText(String.valueOf(selected.size()));
            selBar.getChildAt(6).setVisibility(selected.size() == 1 ? View.VISIBLE : View.GONE);
        }
        adapter.notifyDataSetChanged();
    }

    private ArrayList<Msg> selectedMsgs() {
        ArrayList<Msg> out = new ArrayList<Msg>();
        for (int i = 0; i < items.size(); i++) {
            if (selected.contains(Long.valueOf(items.get(i).id))) {
                out.add(items.get(i));
            }
        }
        return out;
    }

    private String joinBodies(ArrayList<Msg> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(l.get(i).body);
        }
        return sb.toString();
    }

    private void runSelectionAction(int action) {
        final ArrayList<Msg> sel = selectedMsgs();
        if (sel.size() == 0) {
            return;
        }
        switch (action) {
            case 1:
                Ui.copy(this, joinBodies(sel));
                Ui.toast(this, R.string.copied);
                clearSelection();
                break;
            case 2: {
                Intent i = new Intent(this, NewMessageActivity.class);
                i.putExtra("forward_text", joinBodies(sel));
                startActivity(i);
                clearSelection();
                break;
            }
            case 3: {
                boolean all = true;
                for (int j = 0; j < sel.size(); j++) {
                    if (!starredCache.contains(Long.valueOf(sel.get(j).id))) {
                        all = false;
                    }
                }
                for (int j = 0; j < sel.size(); j++) {
                    Long k = Long.valueOf(sel.get(j).id);
                    Prefs.setStarred(this, sel.get(j).id, !all);
                    if (all) {
                        starredCache.remove(k);
                    } else {
                        starredCache.add(k);
                    }
                }
                Ui.toast(this, all ? R.string.unstarred : R.string.starred);
                clearSelection();
                break;
            }
            case 4: {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, joinBodies(sel));
                startActivity(Intent.createChooser(i, getString(R.string.share)));
                clearSelection();
                break;
            }
            case 5:
                showDetails(sel.get(0));
                break;
            case 6: {
                if (!SmsUtil.isDefault(this)) {
                    Ui.toast(this, R.string.need_default);
                    return;
                }
                new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.delete_msgs_confirm, sel.size()))
                    .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            for (int j = 0; j < sel.size(); j++) {
                                SmsUtil.deleteMessage(ThreadActivity.this, sel.get(j).id);
                            }
                            clearSelection();
                            load();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
                break;
            }
        }
    }

    private void showDetails(Msg m) {
        int[] li = SmsUtil.lengthInfo(m.body);
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.d_type)).append(": ")
            .append(getString(m.out ? R.string.d_sent : R.string.d_received)).append('\n');
        sb.append(getString(m.out ? R.string.d_to : R.string.d_from)).append(": ")
            .append(address).append('\n');
        sb.append(getString(R.string.d_time)).append(": ")
            .append(SmsUtil.fullDate(this, m.date)).append('\n');
        sb.append(getString(R.string.d_size)).append(": ")
            .append(getString(R.string.d_size_val, m.body.length(), li[0])).append('\n');
        if (m.out) {
            sb.append(getString(R.string.d_status)).append(": ").append(statusText(m, true));
        }
        new AlertDialog.Builder(this)
            .setTitle(R.string.d_title)
            .setMessage(sb.toString())
            .setPositiveButton(R.string.close, null)
            .show();
    }

    // ---------------------------------------------------------------- overflow menu

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        m.add(0, 1, 0, R.string.menu_chat_style);
        int sc = Scheduler.forAddress(this, address).size();
        m.add(0, 2, 1, sc > 0 ? getString(R.string.menu_scheduled_n, sc)
            : getString(R.string.menu_scheduled));
        m.add(0, 3, 2, Prefs.isPinned(this, address) ? R.string.unpin : R.string.pin);
        m.add(0, 4, 3, Prefs.isMuted(this, address) ? R.string.unmute : R.string.mute);
        m.add(0, 5, 4, Prefs.isBlocked(this, address) ? R.string.unblock : R.string.block);
        if (info != null && info.found && info.lookupKey != null) {
            m.add(0, 6, 5, R.string.view_contact);
        } else {
            m.add(0, 7, 5, R.string.add_contact);
        }
        m.add(0, 8, 6, R.string.export_chat);
        m.add(0, 9, 7, R.string.delete_conversation);
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return onMenu(item.getItemId());
            }
        });
        pm.show();
    }

    private boolean onMenu(int id) {
        switch (id) {
            case 1: {
                Intent i = new Intent(this, StyleActivity.class);
                i.putExtra("address", address);
                startActivity(i);
                return true;
            }
            case 2:
                showScheduled();
                return true;
            case 3:
                Prefs.setPinned(this, address, !Prefs.isPinned(this, address));
                return true;
            case 4: {
                boolean now = !Prefs.isMuted(this, address);
                Prefs.setMuted(this, address, now);
                Ui.toast(this, now ? R.string.muted_toast : R.string.unmuted_toast);
                return true;
            }
            case 5: {
                boolean now = !Prefs.isBlocked(this, address);
                Prefs.setBlocked(this, address, now);
                Ui.toast(this, now ? R.string.blocked_toast : R.string.unblocked);
                bindHeader();
                return true;
            }
            case 6:
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                        ContactsContract.Contacts.getLookupUri(info.contactId, info.lookupKey)));
                } catch (Exception e) {
                    Ui.toast(this, R.string.no_app);
                }
                return true;
            case 7:
                try {
                    Intent i = new Intent(ContactsContract.Intents.Insert.ACTION);
                    i.setType(ContactsContract.RawContacts.CONTENT_TYPE);
                    i.putExtra(ContactsContract.Intents.Insert.PHONE, address);
                    startActivity(i);
                } catch (Exception e) {
                    Ui.toast(this, R.string.no_app);
                }
                return true;
            case 8:
                exportChat();
                return true;
            case 9:
                deleteConversation();
                return true;
        }
        return false;
    }

    private void deleteConversation() {
        if (!SmsUtil.isDefault(this)) {
            Ui.toast(this, R.string.need_default);
            return;
        }
        new AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_convs_confirm, 1))
            .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SmsUtil.deleteThread(ThreadActivity.this, address);
                    Prefs.setDraft(ThreadActivity.this, address, "");
                    finish();
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void exportChat() {
        final String who = info != null && info.name != null ? info.name : address;
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                StringBuilder sb = new StringBuilder();
                sb.append(who).append(" (").append(address).append(")\n\n");
                Cursor c = null;
                try {
                    String sel = threadId > 0 ? "thread_id = ?" : "address = ?";
                    String[] args = new String[] { threadId > 0 ? String.valueOf(threadId) : address };
                    c = getContentResolver().query(Telephony.Sms.CONTENT_URI,
                        new String[] { "body", "date", "type" }, sel, args, "date ASC");
                    while (c != null && c.moveToNext() && sb.length() < 400000) {
                        sb.append('[').append(SmsUtil.fullDate(ThreadActivity.this, c.getLong(1)))
                            .append("] ")
                            .append(c.getInt(2) == 1 ? who : getString(R.string.me))
                            .append(": ").append(c.getString(0)).append('\n');
                    }
                } catch (Exception e) {
                    // export what we have
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
                return sb.toString();
            }

            @Override
            protected void onPostExecute(String text) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_SUBJECT, who);
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, getString(R.string.export_chat)));
            }
        }.execute();
    }

    // ---------------------------------------------------------------- adapter

    private String statusText(Msg m, boolean verbose) {
        if (!m.out) {
            return "";
        }
        if (m.type == SmsUtil.TYPE_OUTBOX || m.type == 6) {
            return getString(R.string.st_sending);
        }
        if (m.type == SmsUtil.TYPE_FAILED) {
            return getString(R.string.st_failed);
        }
        if (m.status == 0) {
            return "\u2713\u2713 " + getString(R.string.st_delivered);
        }
        if (m.status >= 64) {
            return getString(R.string.st_delivery_failed);
        }
        return "\u2713 " + getString(R.string.st_sent);
    }

    private View buildMsgRow() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new AbsListView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Holder h = new Holder();

        h.dateHeader = tv("", 12, look.text2, true);
        h.dateHeader.setGravity(Gravity.CENTER);
        h.dateHeader.setPadding(dp(12), dp(4), dp(12), dp(4));
        h.dateHeader.setBackground(Ui.rect(Look.alpha(look.surface, 0xE8), dp(12)));
        LinearLayout.LayoutParams dp1 = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        dp1.gravity = Gravity.CENTER_HORIZONTAL;
        dp1.setMargins(0, dp(10), 0, dp(8));
        root.addView(h.dateHeader, dp1);

        h.row = new LinearLayout(this);
        h.row.setOrientation(LinearLayout.HORIZONTAL);
        h.row.setGravity(Gravity.BOTTOM);
        h.avatar = new ImageView(this);
        h.avatar.setScaleType(ImageView.ScaleType.FIT_XY);
        LinearLayout.LayoutParams avp = lp(dp(28), dp(28));
        avp.setMargins(0, 0, dp(6), dp(2));
        h.row.addView(h.avatar, avp);

        h.col = new LinearLayout(this);
        h.col.setOrientation(LinearLayout.VERTICAL);
        h.bubble = new LinkTextView(this);
        h.col.addView(h.bubble, lp(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        h.chip = tv("", 13, look.accent, true);
        h.chip.setPadding(dp(12), dp(5), dp(12), dp(5));
        LinearLayout.LayoutParams chp = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        chp.setMargins(dp(2), dp(4), dp(2), 0);
        h.col.addView(h.chip, chp);
        h.meta = tv("", 11, look.text2, false);
        LinearLayout.LayoutParams mp = lp(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        mp.setMargins(dp(6), dp(2), dp(6), 0);
        h.col.addView(h.meta, mp);
        h.row.addView(h.col, lp(LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(h.row, lp(LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT));
        root.setTag(h);
        return root;
    }

    private View buildLoadMore() {
        TextView t = tv(getString(R.string.load_earlier), 14, look.accent, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(16), dp(12), dp(16), dp(12));
        t.setLayoutParams(new AbsListView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private class MsgAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return items.size() + (hasMore ? 1 : 0);
        }

        @Override
        public Object getItem(int position) {
            return msgAt(position);
        }

        @Override
        public long getItemId(int position) {
            Msg m = msgAt(position);
            return m == null ? -1 : m.id;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return (hasMore && position == 0) ? 1 : 0;
        }

        @Override
        public View getView(int position, View v, ViewGroup parent) {
            if (hasMore && position == 0) {
                return v != null ? v : buildLoadMore();
            }
            if (v == null) {
                v = buildMsgRow();
            }
            Holder h = (Holder) v.getTag();
            final Msg m = msgAt(position);
            if (m == null) {
                return v;
            }
            boolean isSel = selected.contains(Long.valueOf(m.id));
            boolean isFlash = flashId == m.id;
            if (isSel) {
                v.setBackgroundColor(Look.alpha(look.accent, 0x30));
            } else if (isFlash) {
                v.setBackgroundColor(Look.alpha(look.accent, 0x44));
            } else {
                v.setBackgroundColor(0);
            }
            v.setPadding(dp(10), m.joinPrev ? dp(1) : dp(5), dp(10), 0);

            if (m.showDate) {
                h.dateHeader.setVisibility(View.VISIBLE);
                h.dateHeader.setText(SmsUtil.dayLabel(ThreadActivity.this, m.date));
            } else {
                h.dateHeader.setVisibility(View.GONE);
            }

            h.row.setGravity((m.out ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM);
            h.col.setGravity(m.out ? Gravity.RIGHT : Gravity.LEFT);

            if (!m.out && avatars) {
                if (m.joinNext) {
                    h.avatar.setVisibility(View.INVISIBLE);
                } else {
                    h.avatar.setVisibility(View.VISIBLE);
                    h.avatar.setImageDrawable(Contacts.avatar(address, info));
                }
            } else {
                h.avatar.setVisibility(View.GONE);
            }

            int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.76f);
            h.bubble.setMaxWidth(maxW);
            h.bubble.setTextSize(textSizeSp);
            h.bubble.setTypeface(BubbleDrawable.typeface(styleId));
            int tc = BubbleDrawable.textColor(styleId, m.out, look);
            h.bubble.setTextColor(tc);
            h.bubble.setLinkTextColor(m.out ? tc : look.accent);
            h.bubble.setLinksEnabled(selected.size() == 0);
            h.bubble.setBackground(new BubbleDrawable(styleId, m.out, m.joinPrev, m.joinNext,
                dens, roundness, look));

            SpannableString sp = new SpannableString(m.body);
            Linkify.addLinks(sp, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            if (searchQuery.length() > 0) {
                String low = m.body.toLowerCase(Locale.getDefault());
                String q = searchQuery.toLowerCase(Locale.getDefault());
                int from = 0;
                while (true) {
                    int at = low.indexOf(q, from);
                    if (at < 0) {
                        break;
                    }
                    sp.setSpan(new BackgroundColorSpan(0x99FFD54F), at, at + q.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    from = at + q.length();
                }
            }
            h.bubble.setText(sp, TextView.BufferType.SPANNABLE);

            if (m.otp != null) {
                final String code = m.otp;
                h.chip.setVisibility(View.VISIBLE);
                h.chip.setText(getString(R.string.copy_code, code));
                h.chip.setTextColor(look.accent);
                h.chip.setBackground(Ui.rectStroke(look.bg, Look.alpha(look.accent, 0xA0),
                    dp(1), dp(14)));
                h.chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Ui.copy(ThreadActivity.this, code);
                        Ui.toast(ThreadActivity.this, R.string.code_copied);
                    }
                });
            } else {
                h.chip.setVisibility(View.GONE);
            }

            boolean problem = m.type == SmsUtil.TYPE_FAILED || m.type == SmsUtil.TYPE_OUTBOX;
            boolean showMeta = !m.joinNext || timeAlways || problem
                || expanded.contains(Long.valueOf(m.id));
            if (showMeta) {
                StringBuilder sb = new StringBuilder();
                sb.append(SmsUtil.clock(ThreadActivity.this, m.date));
                if (m.out) {
                    boolean lastOut = true;
                    for (int i = position - (hasMore ? 1 : 0) + 1; i < items.size(); i++) {
                        if (items.get(i).out) {
                            lastOut = false;
                            break;
                        }
                    }
                    if (lastOut || problem || expanded.contains(Long.valueOf(m.id))) {
                        sb.append("  ").append(statusText(m, false));
                    }
                }
                int slotN = Sims.slotNumber(ThreadActivity.this, m.subId);
                if (slotN > 0) {
                    sb.append("  \u00b7 SIM ").append(slotN);
                }
                if (starredCache.contains(Long.valueOf(m.id))) {
                    sb.append("  \u2605");
                }
                h.meta.setVisibility(View.VISIBLE);
                h.meta.setText(sb.toString());
                h.meta.setTextColor(m.type == SmsUtil.TYPE_FAILED ? look.danger : look.text2);
                // meta sits on the wallpaper, so give it a soft pill to stay readable
                h.meta.setBackground(Ui.rect(Look.alpha(look.surface, 0xB0), dp(8)));
                h.meta.setPadding(dp(6), dp(1), dp(6), dp(1));
            } else {
                h.meta.setVisibility(View.GONE);
            }
            return v;
        }
    }
}
