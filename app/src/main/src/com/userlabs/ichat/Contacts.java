package com.userlabs.ichat;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.LruCache;
import java.io.InputStream;

/** Cached contact lookup (name, photo, id) keyed by normalised number. */
public class Contacts {

    public static class Info {
        public String name;
        public Bitmap photo;
        public boolean found;
        public long contactId;
        public String lookupKey;
    }

    private static final int[] PALETTE = {
        0xFF4F8EF7, 0xFFE5547A, 0xFF3DBE8B, 0xFFF29C38, 0xFF8A63D2,
        0xFF2BB5C9, 0xFFE2574C, 0xFF6C7A89, 0xFF9AA82E, 0xFFC4703A
    };

    private static final LruCache<String, Info> cache = new LruCache<String, Info>(400);

    public static Info cached(String number) {
        if (number == null) {
            return null;
        }
        return cache.get(Prefs.key(number));
    }

    public static void clear() {
        cache.evictAll();
    }

    /** Blocking lookup; call from a background thread. Never returns null. */
    public static Info lookup(Context c, String number) {
        Info hit = cached(number);
        if (hit != null) {
            return hit;
        }
        Info info = new Info();
        info.name = number == null ? "" : number;
        if (number != null && number.length() > 0) {
            Cursor cur = null;
            try {
                Uri uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
                cur = c.getContentResolver().query(uri,
                    new String[] {
                        ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup._ID,
                        ContactsContract.PhoneLookup.LOOKUP_KEY,
                        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
                    }, null, null, null);
                if (cur != null && cur.moveToFirst()) {
                    String n = cur.getString(0);
                    if (n != null && n.length() > 0) {
                        info.name = n;
                        info.found = true;
                    }
                    info.contactId = cur.getLong(1);
                    info.lookupKey = cur.getString(2);
                    String photo = cur.getString(3);
                    if (photo != null) {
                        InputStream in = null;
                        try {
                            in = c.getContentResolver().openInputStream(Uri.parse(photo));
                            if (in != null) {
                                info.photo = BitmapFactory.decodeStream(in);
                            }
                        } catch (Exception e) {
                            // no photo available
                        } finally {
                            if (in != null) {
                                try {
                                    in.close();
                                } catch (Exception e2) {
                                    // ignore
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // no permission or lookup failure: fall back to the number
            } finally {
                if (cur != null) {
                    cur.close();
                }
            }
        }
        if (number != null) {
            cache.put(Prefs.key(number), info);
        }
        return info;
    }

    public static int colorFor(String address) {
        String k = Prefs.key(address);
        int h = 0;
        for (int i = 0; i < k.length(); i++) {
            h = h * 31 + k.charAt(i);
        }
        if (h < 0) {
            h = -h;
        }
        if (h < 0) {
            h = 0;
        }
        return PALETTE[h % PALETTE.length];
    }

    public static String initial(String label) {
        if (label == null) {
            return "#";
        }
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            if (Character.isLetter(ch)) {
                return String.valueOf(ch).toUpperCase();
            }
        }
        return "#";
    }

    public static AvatarDrawable avatar(String address, Info info) {
        String label = (info != null && info.name != null && info.name.length() > 0)
            ? info.name : address;
        return new AvatarDrawable(colorFor(address), initial(label),
            info != null ? info.photo : null);
    }
}
