package com.userlabs.ichat;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Chat wallpapers drawn in code: gradients and tiling patterns that follow the accent colour. */
public class WallpaperDrawable extends Drawable {

    public static final int DEFAULT = 0;
    public static final int SUNRISE = 1;
    public static final int OCEAN = 2;
    public static final int FOREST = 3;
    public static final int LAVENDER = 4;
    public static final int MIDNIGHT = 5;
    public static final int DOTS = 6;
    public static final int DOODLE = 7;
    public static final int WAVES = 8;
    public static final int AURORA = 9;
    public static final int GRID = 10;
    public static final int DIAMONDS = 11;
    public static final int SUNSET = 12;
    public static final int PAPER = 13;
    public static final int BLACK = 14;
    public static final int COUNT = 15;

    private final int id;
    private final Look k;
    private final float d;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();

    public WallpaperDrawable(int id, Look look, float density) {
        this.id = id;
        this.k = look;
        this.d = density;
    }

    /** True when the wallpaper is dark enough that text drawn directly on it must be light. */
    public static boolean isDark(int id, Look k) {
        switch (id) {
            case OCEAN:
            case MIDNIGHT:
            case AURORA:
            case BLACK:
            case SUNSET:
                return true;
            case SUNRISE:
            case FOREST:
            case LAVENDER:
            case PAPER:
                return false;
            default:
                return k.dark;
        }
    }

    public static int nameRes(int id) {
        switch (id) {
            case DEFAULT: return R.string.wp_default;
            case SUNRISE: return R.string.wp_sunrise;
            case OCEAN: return R.string.wp_ocean;
            case FOREST: return R.string.wp_forest;
            case LAVENDER: return R.string.wp_lavender;
            case MIDNIGHT: return R.string.wp_midnight;
            case DOTS: return R.string.wp_dots;
            case DOODLE: return R.string.wp_doodle;
            case WAVES: return R.string.wp_waves;
            case AURORA: return R.string.wp_aurora;
            case GRID: return R.string.wp_grid;
            case DIAMONDS: return R.string.wp_diamonds;
            case SUNSET: return R.string.wp_sunset;
            case PAPER: return R.string.wp_paper;
            default: return R.string.wp_black;
        }
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) {
            return;
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        switch (id) {
            case SUNRISE:
                vertical(c, b, new int[] { 0xFFFFE3C8, 0xFFFFB5C2 });
                break;
            case OCEAN:
                vertical(c, b, new int[] { 0xFF0F2027, 0xFF203A43, 0xFF2C5364 });
                break;
            case FOREST:
                vertical(c, b, new int[] { 0xFFE3F6C8, 0xFF9ADBA9 });
                break;
            case LAVENDER:
                vertical(c, b, new int[] { 0xFFE9D5FF, 0xFFA5C8FF });
                break;
            case MIDNIGHT:
                vertical(c, b, new int[] { 0xFF0F0C29, 0xFF302B63, 0xFF24243E });
                break;
            case SUNSET:
                vertical(c, b, new int[] { 0xFFFFB27A, 0xFFFF6B8B, 0xFF7B4FD6 });
                break;
            case BLACK:
                solid(c, b, 0xFF000000);
                break;
            case PAPER:
                solid(c, b, 0xFFF3EEE3);
                dots(c, b, 0x22806040);
                break;
            case DOTS:
                solid(c, b, k.bg);
                dots(c, b, Look.alpha(k.accent, 0x46));
                break;
            case DOODLE:
                solid(c, b, k.bg);
                doodle(c, b);
                break;
            case WAVES:
                solid(c, b, k.bg);
                waves(c, b);
                break;
            case GRID:
                solid(c, b, k.bg);
                grid(c, b);
                break;
            case DIAMONDS:
                solid(c, b, k.bg);
                diamonds(c, b);
                break;
            case AURORA:
                solid(c, b, 0xFF0B1020);
                aurora(c, b);
                break;
            default:
                solid(c, b, k.bg);
                break;
        }
    }

    private void solid(Canvas c, Rect b, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRect(b, paint);
    }

    private void vertical(Canvas c, Rect b, int[] colors) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setShader(new LinearGradient(b.left, b.top, b.right * 0.6f, b.bottom,
            colors, null, Shader.TileMode.CLAMP));
        c.drawRect(b, paint);
        paint.setShader(null);
    }

    private void dots(Canvas c, Rect b, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        float step = 22f * d;
        int row = 0;
        for (float y = b.top + step / 2f; y < b.bottom + step; y += step) {
            float off = (row % 2 == 0) ? 0f : step / 2f;
            for (float x = b.left + off; x < b.right + step; x += step) {
                c.drawCircle(x, y, 1.7f * d, paint);
            }
            row++;
        }
    }

    private static int hash(int i, int j) {
        int h = i * 73856093 ^ j * 19349663;
        h ^= (h >>> 13);
        h *= 0x5bd1e995;
        h ^= (h >>> 15);
        return h & 0x7fffffff;
    }

    private void doodle(Canvas c, Rect b) {
        float cell = 58f * d;
        int cols = (int) (b.width() / cell) + 2;
        int rows = (int) (b.height() / (cell * 0.85f)) + 2;
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.3f * d);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(Look.alpha(k.accent, 0x3A));
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                int h = hash(i, j);
                float cx = b.left + i * cell + ((j % 2) * cell / 2f) + ((h >> 3) & 15) / 15f * cell * 0.4f;
                float cy = b.top + j * cell * 0.85f + ((h >> 7) & 15) / 15f * cell * 0.3f;
                float ang = (h & 63) * 5.6f;
                int shape = (h >> 9) % 5;
                c.save();
                c.translate(cx, cy);
                c.rotate(ang);
                switch (shape) {
                    case 0:
                        c.drawCircle(0f, 0f, 5f * d, paint);
                        break;
                    case 1:
                        c.drawLine(-5f * d, 0f, 5f * d, 0f, paint);
                        c.drawLine(0f, -5f * d, 0f, 5f * d, paint);
                        break;
                    case 2:
                        path.reset();
                        path.moveTo(0f, -6f * d);
                        path.lineTo(5.5f * d, 4f * d);
                        path.lineTo(-5.5f * d, 4f * d);
                        path.close();
                        c.drawPath(path, paint);
                        break;
                    case 3:
                        rf.set(-7f * d, -4.5f * d, 7f * d, 4.5f * d);
                        c.drawRoundRect(rf, 3.5f * d, 3.5f * d, paint);
                        break;
                    default:
                        path.reset();
                        path.moveTo(-8f * d, 0f);
                        path.quadTo(-4f * d, -6f * d, 0f, 0f);
                        path.quadTo(4f * d, 6f * d, 8f * d, 0f);
                        c.drawPath(path, paint);
                        break;
                }
                c.restore();
            }
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void waves(Canvas c, Rect b) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f * d);
        paint.setColor(Look.alpha(k.accent, 0x34));
        float gap = 36f * d;
        float amp = 8f * d;
        float wl = 130f * d;
        int band = 0;
        for (float y0 = b.top - gap; y0 < b.bottom + gap; y0 += gap) {
            path.reset();
            boolean first = true;
            for (float x = b.left; x <= b.right + 8f * d; x += 8f * d) {
                float y = y0 + (float) Math.sin((x / wl) * 6.2831853 + band * 0.9) * amp;
                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            }
            c.drawPath(path, paint);
            band++;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void grid(Canvas c, Rect b) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        float cell = 26f * d;
        int n = 0;
        for (float x = b.left; x <= b.right; x += cell) {
            paint.setStrokeWidth((n % 5 == 0 ? 1.3f : 0.7f) * d);
            paint.setColor(Look.alpha(k.accent, n % 5 == 0 ? 0x38 : 0x1C));
            c.drawLine(x, b.top, x, b.bottom, paint);
            n++;
        }
        n = 0;
        for (float y = b.top; y <= b.bottom; y += cell) {
            paint.setStrokeWidth((n % 5 == 0 ? 1.3f : 0.7f) * d);
            paint.setColor(Look.alpha(k.accent, n % 5 == 0 ? 0x38 : 0x1C));
            c.drawLine(b.left, y, b.right, y, paint);
            n++;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void diamonds(Canvas c, Rect b) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.1f * d);
        paint.setColor(Look.alpha(k.accent, 0x30));
        float s = 17f * d;
        int row = 0;
        for (float y = b.top; y < b.bottom + s * 2f; y += s) {
            float off = (row % 2 == 0) ? 0f : s;
            for (float x = b.left + off; x < b.right + s * 2f; x += s * 2f) {
                path.reset();
                path.moveTo(x, y - s);
                path.lineTo(x + s, y);
                path.lineTo(x, y + s);
                path.lineTo(x - s, y);
                path.close();
                c.drawPath(path, paint);
            }
            row++;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void aurora(Canvas c, Rect b) {
        float w = b.width();
        float h = b.height();
        blob(c, b.left + w * 0.2f, b.top + h * 0.12f, w * 0.75f, Look.alpha(k.accent, 0xB0));
        blob(c, b.left + w * 0.9f, b.top + h * 0.5f, w * 0.8f, Look.alpha(k.accent2, 0x98));
        blob(c, b.left + w * 0.25f, b.top + h * 0.96f, w * 0.8f, 0x7722D3EE);
    }

    private void blob(Canvas c, float cx, float cy, float radius, int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setShader(new RadialGradient(cx, cy, radius, color, color & 0x00FFFFFF,
            Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, radius, paint);
        paint.setShader(null);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
