package com.userlabs.ichat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Self-drawn on/off switch that follows the accent colour (no framework Switch tinting needed). */
public class ToggleView extends View {

    public interface Listener {
        void onToggled(ToggleView v, boolean on);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private boolean on;
    private float pos; // 0..1
    private int onColor = 0xFF0A84FF;
    private int offColor = 0xFFB8BEC8;
    private Listener listener;

    public ToggleView(Context c) {
        super(c);
        setClickable(true);
    }

    public void setColors(int onColor, int offColor) {
        this.onColor = onColor;
        this.offColor = offColor;
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean isOn() {
        return on;
    }

    public void setOn(boolean value, boolean animate) {
        on = value;
        if (animate) {
            ValueAnimator a = ValueAnimator.ofFloat(pos, on ? 1f : 0f);
            a.setDuration(160);
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator va) {
                    pos = ((Float) va.getAnimatedValue()).floatValue();
                    invalidate();
                }
            });
            a.start();
        } else {
            pos = on ? 1f : 0f;
            invalidate();
        }
    }

    /** Flips the switch as if the user had tapped it. */
    public void performClickToggle() {
        setOn(!on, true);
        if (listener != null) {
            listener.onToggled(this, on);
        }
    }

    @Override
    protected void onMeasure(int w, int h) {
        float d = getResources().getDisplayMetrics().density;
        setMeasuredDimension((int) (46 * d), (int) (28 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float d = getResources().getDisplayMetrics().density;
        float th = 22f * d;
        float top = (h - th) / 2f;
        rf.set(2f * d, top, w - 2f * d, top + th);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Look.blend(offColor, onColor, pos));
        c.drawRoundRect(rf, th / 2f, th / 2f, paint);
        float kr = 9f * d;
        float cx = rf.left + th / 2f + pos * (rf.width() - th);
        paint.setColor(0x33000000);
        c.drawCircle(cx, h / 2f + 1f * d, kr, paint);
        paint.setColor(0xFFFFFFFF);
        c.drawCircle(cx, h / 2f, kr, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && isEnabled()) {
            setOn(!on, true);
            if (listener != null) {
                listener.onToggled(this, on);
            }
            performClick();
            return true;
        }
        return super.onTouchEvent(e);
    }
}
