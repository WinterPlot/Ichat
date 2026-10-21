package com.userlabs.ichat;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** Round avatar: contact photo when available, otherwise a coloured disc with an initial. */
public class AvatarDrawable extends Drawable {

    private final int color;
    private final String letter;
    private final Bitmap photo;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private BitmapShader shader;

    public AvatarDrawable(int color, String letter, Bitmap photo) {
        this.color = color;
        this.letter = letter;
        this.photo = photo;
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        if (photo != null) {
            shader = new BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) {
            return;
        }
        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        float r = Math.min(b.width(), b.height()) / 2f;
        if (shader != null && photo != null) {
            float s = Math.max(2f * r / photo.getWidth(), 2f * r / photo.getHeight());
            matrix.reset();
            matrix.setScale(s, s);
            matrix.postTranslate(cx - photo.getWidth() * s / 2f, cy - photo.getHeight() * s / 2f);
            shader.setLocalMatrix(matrix);
            paint.setShader(shader);
            c.drawCircle(cx, cy, r, paint);
            paint.setShader(null);
        } else {
            paint.setShader(null);
            paint.setColor(color);
            c.drawCircle(cx, cy, r, paint);
            textPaint.setTextSize(r * 0.95f);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            c.drawText(letter, cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        textPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
