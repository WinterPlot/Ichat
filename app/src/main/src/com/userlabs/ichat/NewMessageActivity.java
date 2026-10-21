package com.userlabs.ichat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/** Pick a contact (or type a number) to start a conversation; also the target of "Forward". */
public class NewMessageActivity extends BaseActivity {

    static class Entry {
        String name;
        String number;
        boolean typed;
    }

    private static final int REQ = 7;

    private final ArrayList<Entry> all = new ArrayList<Entry>();
    private final ArrayList<Entry> shown = new ArrayList<Entry>();
    private EditText toInput;
    private TextView hint;
    private ContactAdapter adapter;
    private String forwardText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        forwardText = getIntent().getStringExtra("forward_text");
        if (forwardText == null && Intent.ACTION_SEND.equals(getIntent().getAction())) {
            forwardText = getIntent().getStringExtra(Intent.EXTRA_TEXT);
        }
        setContentView(buildUi());
        Look.window(this, look, look.bg);
        if (hasContacts()) {
            loadContacts();
        } else if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[] { Manifest.permission.READ_CONTACTS }, REQ);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ && hasContacts()) {
            loadContacts();
        }
    }

    private boolean hasContacts() {
        return Build.VERSION.SDK_INT < 23
            || checkSelfPermission(Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED;
    }

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
        bar.addView(tv(getString(forwardText != null ? R.string.forward_to : R.string.new_message_title),
            20, look.text, true), weight(1f));
        root.addView(bar);

        LinearLayout toRow = new LinearLayout(this);
        toRow.setOrientation(LinearLayout.HORIZONTAL);
        toRow.setGravity(Gravity.CENTER_VERTICAL);
        toRow.setPadding(dp(16), dp(4), dp(16), dp(4));
        toRow.setBackground(Ui.rect(look.surface, 0f));
        toRow.addView(tv(getString(R.string.to_label), 15, look.text2, true));
        toInput = new EditText(this);
        toInput.setHint(R.string.to_hint);
        toInput.setHintTextColor(look.text2);
        toInput.setTextColor(look.text);
        toInput.setSingleLine(true);
        toInput.setBackgroundColor(0);
        toInput.setTextSize(17);
        toInput.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams tp = weight(1f);
        tp.setMargins(dp(10), 0, 0, 0);
        toRow.addView(toInput, tp);
        root.addView(toRow);
        root.addView(hline());

        toInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                filter(s.toString().trim());
            }
        });

        hint = tv("", 15, look.text2, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(24), dp(24), dp(24), dp(24));
        hint.setVisibility(View.GONE);
        root.addView(hint);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0));
        list.setFastScrollEnabled(true);
        adapter = new ContactAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                open(shown.get(pos).number);
            }
        });
        root.addView(list, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        Ui.showKeyboard(this, toInput);
        return root;
    }

    private void open(String number) {
        Intent i = new Intent(this, ThreadActivity.class);
        i.putExtra("address", number);
        if (forwardText != null) {
            i.putExtra("forward_text", forwardText);
        }
        Ui.hideKeyboard(this);
        startActivity(i);
        finish();
    }

    private void loadContacts() {
        new AsyncTask<Void, Void, ArrayList<Entry>>() {
            @Override
            protected ArrayList<Entry> doInBackground(Void... params) {
                ArrayList<Entry> out = new ArrayList<Entry>();
                HashSet<String> seen = new HashSet<String>();
                Cursor c = null;
                try {
                    c = getContentResolver().query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[] {
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER },
                        null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE NOCASE ASC");
                    while (c != null && c.moveToNext()) {
                        String n = c.getString(1);
                        if (n == null || n.length() == 0) {
                            continue;
                        }
                        String key = Prefs.key(n);
                        if (!seen.add(key)) {
                            continue;
                        }
                        Entry e = new Entry();
                        e.name = c.getString(0);
                        e.number = n;
                        out.add(e);
                    }
                } catch (Exception ex) {
                    // no contacts available
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
                return out;
            }

            @Override
            protected void onPostExecute(ArrayList<Entry> r) {
                all.clear();
                all.addAll(r);
                filter(toInput.getText().toString().trim());
            }
        }.execute();
    }

    private static boolean looksLikeNumber(String q) {
        int digits = 0;
        for (int i = 0; i < q.length(); i++) {
            char ch = q.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digits++;
            } else if (ch != '+' && ch != ' ' && ch != '-' && ch != '(' && ch != ')') {
                return false;
            }
        }
        return digits >= 3;
    }

    private void filter(String q) {
        shown.clear();
        if (q.length() > 0 && looksLikeNumber(q)) {
            Entry e = new Entry();
            e.typed = true;
            e.number = q.replace(" ", "");
            e.name = getString(R.string.send_to, q);
            shown.add(e);
        }
        String ql = q.toLowerCase(Locale.getDefault());
        String qd = q.replaceAll("[^0-9]", "");
        for (int i = 0; i < all.size(); i++) {
            Entry e = all.get(i);
            boolean ok = ql.length() == 0;
            if (!ok && e.name != null && e.name.toLowerCase(Locale.getDefault()).contains(ql)) {
                ok = true;
            }
            if (!ok && qd.length() > 0 && e.number.replaceAll("[^0-9]", "").contains(qd)) {
                ok = true;
            }
            if (ok) {
                shown.add(e);
            }
        }
        adapter.notifyDataSetChanged();
        if (shown.size() == 0) {
            hint.setVisibility(View.VISIBLE);
            hint.setText(hasContacts() ? R.string.no_contacts_match : R.string.contacts_perm);
        } else {
            hint.setVisibility(View.GONE);
        }
    }

    private class ContactAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Object getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View v, ViewGroup parent) {
            LinearLayout row;
            ImageView av;
            TextView name;
            TextView num;
            if (v == null) {
                row = new LinearLayout(NewMessageActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(8), dp(16), dp(8));
                row.setLayoutParams(new AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                av = new ImageView(NewMessageActivity.this);
                av.setScaleType(ImageView.ScaleType.FIT_XY);
                row.addView(av, lp(dp(42), dp(42)));
                LinearLayout col = new LinearLayout(NewMessageActivity.this);
                col.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams cp = weight(1f);
                cp.setMargins(dp(14), 0, 0, 0);
                name = oneLine("", 16, look.text, false);
                num = oneLine("", 13, look.text2, false);
                col.addView(name);
                col.addView(num);
                row.addView(col, cp);
                row.setTag(new View[] { av, name, num });
                v = row;
                row.setBackground(Ui.ripple(Look.alpha(look.text, 0x14), null, 0f));
            }
            View[] t = (View[]) v.getTag();
            av = (ImageView) t[0];
            name = (TextView) t[1];
            num = (TextView) t[2];
            Entry e = shown.get(position);
            if (e.typed) {
                av.setImageDrawable(Ui.tinted(NewMessageActivity.this, R.drawable.ic_person_add,
                    look.accent));
                name.setText(e.name);
                num.setText("");
                num.setVisibility(View.GONE);
            } else {
                av.setImageDrawable(new AvatarDrawable(Contacts.colorFor(e.number),
                    Contacts.initial(e.name), null));
                name.setText(e.name);
                num.setText(e.number);
                num.setVisibility(View.VISIBLE);
            }
            return v;
        }
    }
}
