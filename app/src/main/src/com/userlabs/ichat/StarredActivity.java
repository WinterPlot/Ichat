package com.userlabs.ichat;

import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Telephony;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;

/** All starred messages across conversations. */
public class StarredActivity extends BaseActivity {

    static class Row {
        long id;
        String address;
        String name;
        String body;
        long date;
        Contacts.Info info;
    }

    private final ArrayList<Row> rows = new ArrayList<Row>();
    private ListView list;
    private TextView empty;
    private RowAdapter adapter;

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
        TextView t = oneLine(getString(R.string.menu_starred), 20, look.text, true);
        t.setPadding(dp(6), 0, 0, 0);
        bar.addView(t, weight(1f));
        root.addView(bar);
        root.addView(hline());

        FrameLayout body = new FrameLayout(this);
        list = new ListView(this);
        list.setDivider(null);
        list.setCacheColorHint(0);
        adapter = new RowAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                Row r = rows.get(pos);
                Intent i = new Intent(StarredActivity.this, ThreadActivity.class);
                i.putExtra("address", r.address);
                i.putExtra("jump_id", r.id);
                startActivity(i);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
                final Row r = rows.get(pos);
                new AlertDialog.Builder(StarredActivity.this)
                    .setMessage(R.string.unstar_q)
                    .setPositiveButton(R.string.unstar, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Prefs.setStarred(StarredActivity.this, r.id, false);
                            load();
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
                return true;
            }
        });
        body.addView(list, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        empty = tv(getString(R.string.no_starred), 15, look.text2, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(32), dp(32), dp(32), dp(32));
        empty.setVisibility(View.GONE);
        body.addView(empty, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(body, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        Look.window(this, look, look.bg);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        new AsyncTask<Void, Void, ArrayList<Row>>() {
            @Override
            protected ArrayList<Row> doInBackground(Void... params) {
                ArrayList<Row> out = new ArrayList<Row>();
                ArrayList<Long> ids = Prefs.starredIds(StarredActivity.this);
                if (ids.size() == 0) {
                    return out;
                }
                StringBuilder in = new StringBuilder();
                for (int i = 0; i < ids.size(); i++) {
                    if (i > 0) {
                        in.append(',');
                    }
                    in.append(ids.get(i).longValue());
                }
                Cursor c = null;
                try {
                    c = getContentResolver().query(Telephony.Sms.CONTENT_URI,
                        new String[] { "_id", "address", "body", "date" },
                        "_id IN (" + in.toString() + ")", null, "date DESC");
                    while (c != null && c.moveToNext()) {
                        Row r = new Row();
                        r.id = c.getLong(0);
                        r.address = c.getString(1);
                        r.body = c.getString(2);
                        r.date = c.getLong(3);
                        if (r.address == null) {
                            continue;
                        }
                        r.info = Contacts.lookup(StarredActivity.this, r.address);
                        r.name = r.info.name;
                        out.add(r);
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
                return out;
            }

            @Override
            protected void onPostExecute(ArrayList<Row> r) {
                rows.clear();
                rows.addAll(r);
                adapter.notifyDataSetChanged();
                empty.setVisibility(rows.size() == 0 ? View.VISIBLE : View.GONE);
            }
        }.execute();
    }

    private class RowAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Object getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View v, ViewGroup parent) {
            Row r = rows.get(position);
            LinearLayout row = new LinearLayout(StarredActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            ImageView av = new ImageView(StarredActivity.this);
            av.setScaleType(ImageView.ScaleType.FIT_XY);
            av.setImageDrawable(Contacts.avatar(r.address, r.info));
            row.addView(av, lp(dp(44), dp(44)));
            LinearLayout col = new LinearLayout(StarredActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cp = weight(1f);
            cp.setMargins(dp(14), 0, 0, 0);
            row.addView(col, cp);
            LinearLayout top = new LinearLayout(StarredActivity.this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.addView(oneLine(r.name, 15, look.text, true), weight(1f));
            top.addView(tv(SmsUtil.formatTime(StarredActivity.this, r.date), 12, look.text2, false));
            col.addView(top);
            TextView body = tv(r.body == null ? "" : r.body, 14, look.text2, false);
            body.setMaxLines(3);
            col.addView(body);
            return row;
        }
    }
}
