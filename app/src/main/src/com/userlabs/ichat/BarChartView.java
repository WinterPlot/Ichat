package com.userlabs.ichat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Minimal stacked bar chart with optional highlight bar and sparse labels. */
public class BarChartView extends View {

    private float[] a = new float[0];
    private float[] b;
    private String[] labels;
    private int colA = 0xFF0A84FF;
    private int colB = 0xFF9BCBFF;
    private int textCol = 0xFF888888;
    private int lineCol = 0x22888888;
    private int hi = -1;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final float d;

    public BarChartView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    /**
     * @param top    values of the upper segment (or the only series)
     * @param bottom values of the lower segment, or null
     */
    public void setData(float[] top, float[] bottom, String[] labels, int colorTop, int colorBottom,
                        int highlight, int textColor, int lineColor) {
        this.a = top;
        this.b = bottom;
        this.labels = labels;
        this.colA = colorTop;
        this.colB = colorBottom;
        this.hi = highlight;
        this.textCol = textColor;
        this.lineCol = lineColor;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, (int) (150 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        int n = a.length;
        if (n == 0) {
            return;
        }
        float w = getWidth();
        float h = getHeight();
        float padX = 6f * d;
        float labelH = 18f * d;
        float chartH = h - labelH - 4f * d;
        float max = 0f;
        for (int i = 0; i < n; i++) {
            float v = a[i] + (b != null ? b[i] : 0f);
            if (v > max) {
                max = v;
            }
        }
        if (max <= 0f) {
            max = 1f;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(lineCol);
        c.drawRect(padX, chartH + 2f * d, w - padX, chartH + 3f * d, paint);

        float slot = (w - padX * 2f) / n;
        float bw = slot * 0.62f;
        float r = Math.min(bw / 2f, 4f * d);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(10f * d);
        for (int i = 0; i < n; i++) {
            float x = padX + slot * i + (slot - bw) / 2f;
            float lowV = b != null ? b[i] : 0f;
            float lowH = chartH * (lowV / max);
            float topH = chartH * (a[i] / max);
            int alpha = (hi < 0 || i == hi) ? 255 : 170;
            if (lowH > 0.5f) {
                paint.setColor(withAlpha(colB, alpha));
                rf.set(x, chartH - lowH, x + bw, chartH);
                c.drawRoundRect(rf, r, r, paint);
            }
            if (topH > 0.5f) {
                paint.setColor(withAlpha(colA, alpha));
                rf.set(x, chartH - lowH - topH, x + bw, chartH - lowH + (lowH > 0.5f ? r : 0f));
                c.drawRoundRect(rf, r, r, paint);
            }
            if (labels != null && i < labels.length && labels[i] != null && labels[i].length() > 0) {
                paint.setColor(textCol);
                c.drawText(labels[i], x + bw / 2f, h - 3f * d, paint);
            }
        }
    }

    private int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
