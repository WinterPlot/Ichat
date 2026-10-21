package com.userlabs.ichat;

import android.content.Context;
import android.text.Layout;
import android.text.Spannable;
import android.text.style.ClickableSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.TextView;

/**
 * TextView whose links are tappable, but which otherwise lets taps and long-presses fall
 * through to the ListView row (so selecting a message still works).
 */
public class LinkTextView extends TextView {

    private boolean linksEnabled = true;
    private ClickableSpan pressed;

    public LinkTextView(Context context) {
        super(context);
    }

    public LinkTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LinkTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setLinksEnabled(boolean on) {
        linksEnabled = on;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        CharSequence t = getText();
        if (!linksEnabled || !(t instanceof Spannable) || getLayout() == null) {
            return false;
        }
        int action = event.getAction();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
            ClickableSpan span = spanAt(event, (Spannable) t);
            if (action == MotionEvent.ACTION_DOWN) {
                pressed = span;
                return span != null;
            }
            ClickableSpan down = pressed;
            pressed = null;
            if (span != null && span == down) {
                span.onClick(this);
                return true;
            }
            return false;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            pressed = null;
        }
        return pressed != null;
    }

    private ClickableSpan spanAt(MotionEvent event, Spannable sp) {
        Layout layout = getLayout();
        int x = (int) event.getX() - getTotalPaddingLeft() + getScrollX();
        int y = (int) event.getY() - getTotalPaddingTop() + getScrollY();
        if (y < 0 || y > layout.getHeight()) {
            return null;
        }
        int line = layout.getLineForVertical(y);
        if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) {
            return null;
        }
        int off = layout.getOffsetForHorizontal(line, x);
        ClickableSpan[] spans = sp.getSpans(off, off, ClickableSpan.class);
        return spans.length > 0 ? spans[0] : null;
    }
}
