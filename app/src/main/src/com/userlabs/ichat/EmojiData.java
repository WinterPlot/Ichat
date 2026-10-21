package com.userlabs.ichat;

import java.util.ArrayList;

/** Emoji palette built from code points (no bundled assets). */
public class EmojiData {

    public static final int CATS = 7;
    public static final String[][] SETS = new String[CATS][];
    public static final String[] TABS = new String[CATS];

    static {
        SETS[0] = join(range(0x1F600, 0x1F64F), range(0x1F910, 0x1F917), range(0x1F920, 0x1F927),
            new String[] { cp(0x1F970), cp(0x1F973), cp(0x1F97A) });
        SETS[1] = join(range(0x1F446, 0x1F450), new String[] { cp(0x1F64C), cp(0x1F64F),
            cp(0x1F4AA), cp(0x270C) + "\uFE0F", cp(0x270B), cp(0x270A), cp(0x1F91D), cp(0x1F91E),
            cp(0x1F918), cp(0x1F919), cp(0x1F44B), cp(0x1F440), cp(0x1F441) + "\uFE0F" });
        SETS[2] = join(new String[] { cp(0x2764) + "\uFE0F" }, range(0x1F493, 0x1F49F),
            new String[] { cp(0x1F5A4), cp(0x1F90D), cp(0x1F90E), cp(0x2728), cp(0x1F525),
                cp(0x1F4AF), cp(0x1F4A5), cp(0x1F4AB), cp(0x2B50), cp(0x1F31F), cp(0x2705),
                cp(0x274C), cp(0x2753), cp(0x2757), cp(0x1F4A4), cp(0x1F389), cp(0x1F38A) });
        SETS[3] = join(range(0x1F400, 0x1F43E), range(0x1F980, 0x1F984));
        SETS[4] = join(range(0x1F345, 0x1F37B), range(0x1F950, 0x1F95E));
        SETS[5] = join(range(0x1F380, 0x1F393), range(0x1F3A0, 0x1F3C4),
            new String[] { cp(0x1F4A1), cp(0x1F4F1), cp(0x1F4BB), cp(0x1F4F7), cp(0x1F381),
                cp(0x1F3B5), cp(0x1F3A4), cp(0x1F4B0), cp(0x1F4DA), cp(0x1F4DD), cp(0x1F512) });
        SETS[6] = join(range(0x1F680, 0x1F6A4), range(0x1F30D, 0x1F30F), range(0x1F3E0, 0x1F3E6));
        for (int i = 0; i < CATS; i++) {
            TABS[i] = SETS[i][0];
        }
        TABS[2] = cp(0x2764) + "\uFE0F";
        TABS[3] = cp(0x1F436);
        TABS[4] = cp(0x1F354);
        TABS[5] = cp(0x26BD);
        TABS[6] = cp(0x1F697);
    }

    public static String cp(int code) {
        return new String(Character.toChars(code));
    }

    private static String[] range(int from, int to) {
        String[] out = new String[to - from + 1];
        for (int i = 0; i < out.length; i++) {
            out[i] = cp(from + i);
        }
        return out;
    }

    private static String[] join(String[]... parts) {
        ArrayList<String> l = new ArrayList<String>();
        for (int i = 0; i < parts.length; i++) {
            for (int j = 0; j < parts[i].length; j++) {
                l.add(parts[i][j]);
            }
        }
        return l.toArray(new String[l.size()]);
    }
}
