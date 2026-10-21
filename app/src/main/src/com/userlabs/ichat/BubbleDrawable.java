package com.userlabs.ichat;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;

/**
 * One drawable that paints every message-bubble style. It owns its own padding so the
 * text never collides with tails, glow or shadows. Neighbouring bubbles of the same sender
 * can "join" (smaller inner corners) to form tidy groups.
 */
public class BubbleDrawable extends Drawable {

    public static final int CLASSIC = 0;
    public static final int TAIL = 1;
    public static final int SPEECH = 2;
    public static final int CAPSULE = 3;
    public static final int SHARP = 4;
    public static final int GRADIENT = 5;
    public static final int OUTLINE = 6;
    public static final int NEON = 7;
    public static final int SOFT = 8;
    public static final int GLASS = 9;
    public static final int STICKER = 10;
    public static final int BAR = 11;
    public static final int TERMINAL = 12;
    public static final int LEAF = 13;
    public static final int PASTEL = 14;
    public static final int CHAMFER = 15;
    public static final int COUNT = 16;

    private final int style;
    private final boolean out;
    private final boolean joinPrev;
    private final boolean joinNext;
    private final float d;
    private final float rs;
    private final Look k;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path tail = new Path();
    private final RectF body = new RectF();
    private final float[] rad = new float[8];
    private int alpha = 255;

    /**
     * @param roundness 0..100 where 50 is the natural roundness of the style
     */
    public BubbleDrawable(int style, boolean out, boolean joinPrev, boolean joinNext,
                          float density, int roundness, Look look) {
        this.style = style;
        this.out = out;
        this.joinPrev = joinPrev;
        this.joinNext = joinNext;
        this.d = density;
        this.rs = 0.35f + (roundness / 100f) * 1.3f;
        this.k = look;
    }

    // ---------------------------------------------------------------- static style info

    public static int nameRes(int style) {
        switch (style) {
            case CLASSIC: return R.string.bs_classic;
            case TAIL: return R.string.bs_tail;
            case SPEECH: return R.string.bs_speech;
            case CAPSULE: return R.string.bs_capsule;
            case SHARP: return R.string.bs_sharp;
            case GRADIENT: return R.string.bs_gradient;
            case OUTLINE: return R.string.bs_outline;
            case NEON: return R.string.bs_neon;
            case SOFT: return R.string.bs_soft;
            case GLASS: return R.string.bs_glass;
            case STICKER: return R.string.bs_sticker;
            case BAR: return R.string.bs_bar;
            case TERMINAL: return R.string.bs_terminal;
            case LEAF: return R.string.bs_leaf;
            case PASTEL: return R.string.bs_pastel;
            default: return R.string.bs_chamfer;
        }
    }

    public static int textColor(int style, boolean out, Look k) {
        switch (style) {
            case OUTLINE:
                return out ? k.accent : k.inText;
            case NEON:
                return out ? 0xFFFFFFFF : 0xFFE5E7EB;
            case STICKER:
                return out ? k.onAccent : 0xFF111111;
            case BAR:
                return k.text;
            case TERMINAL:
                return out ? 0xFF39FF7A : 0xFF9AE6B4;
            case PASTEL:
                if (out) {
                    return k.dark ? Look.lighten(k.accent, 0.35f) : Look.darken(k.accent, 0.55f);
                }
                return k.inText;
            case GLASS:
                return out ? k.onAccent : k.inText;
            default:
                return out ? k.onAccent : k.inText;
        }
    }

    public static Typeface typeface(int style) {
        if (style == TERMINAL) {
            return Typeface.MONOSPACE;
        }
        if (style == STICKER) {
            return Typeface.DEFAULT_BOLD;
        }
        return Typeface.DEFAULT;
    }

    // ---------------------------------------------------------------- geometry

    private float tailW() {
        if (style == TAIL) {
            return 7f * d;
        }
        if (style == SPEECH) {
            return 6f * d;
        }
        return 0f;
    }

    private float baseRadius() {
        switch (style) {
            case CLASSIC: return 18f * d * rs;
            case TAIL: return 9f * d * rs;
            case SPEECH: return 18f * d * rs;
            case CAPSULE: return 1000f * d;
            case SHARP: return 3f * d * rs;
            case GRADIENT: return 20f * d * rs;
            case OUTLINE: return 16f * d * rs;
            case NEON: return 14f * d * rs;
            case SOFT: return 24f * d * rs;
            case GLASS: return 22f * d * rs;
            case STICKER: return 12f * d * rs;
            case BAR: return 6f * d * rs;
            case TERMINAL: return 2f * d * rs;
            case LEAF: return 24f * d * rs;
            case PASTEL: return 16f * d * rs;
            default: return 9f * d * rs; // chamfer cut size
        }
    }

    @Override
    public boolean getPadding(Rect r) {
        float h = 12f * d;
        float v = 8f * d;
        float l = h;
        float t = v;
        float rr = h;
        float b = v;
        switch (style) {
            case CAPSULE:
                l = 16f * d;
                rr = 16f * d;
                break;
            case NEON:
                l += 3f * d;
                rr += 3f * d;
                t += 3f * d;
                b += 3f * d;
                break;
            case STICKER:
                rr += 3f * d;
                b += 3f * d;
                break;
            case SOFT:
                l += 2f * d;
                rr += 2f * d;
                b += 2f * d;
                break;
            case GLASS:
                l += 2f * d;
                rr += 2f * d;
                break;
            case BAR:
                if (out) {
                    rr += 4f * d;
                } else {
                    l += 4f * d;
                }
                break;
            case TERMINAL:
                l += 2f * d;
                rr += 2f * d;
                break;
            case LEAF:
                l += 3f * d;
                rr += 3f * d;
                break;
            case CHAMFER:
                l += 2f * d;
                rr += 2f * d;
                break;
            default:
                break;
        }
        float tw = tailW();
        if (out) {
            rr += tw;
        } else {
            l += tw;
        }
        r.set((int) l, (int) t, (int) rr, (int) b);
        return true;
    }

    /** Sets rad[] for tl, tr, br, bl; inner side is the one facing the screen edge. */
    private void corners(float r, float small, boolean lastSmall) {
        float top = joinPrev ? small : r;
        float bot = (joinNext || lastSmall) ? small : r;
        if (joinNext && !lastSmall) {
            bot = small;
        }
        if (out) {
            setRad(r, top, bot, r);
        } else {
            setRad(top, r, r, bot);
        }
    }

    private void setRad(float tl, float tr, float br, float bl) {
        rad[0] = tl;
        rad[1] = tl;
        rad[2] = tr;
        rad[3] = tr;
        rad[4] = br;
        rad[5] = br;
        rad[6] = bl;
        rad[7] = bl;
    }

    private void roundPath(RectF rect) {
        path.reset();
        path.addRoundRect(rect, rad, Path.Direction.CW);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void draw(Canvas c) {
        Rect bb = getBounds();
        if (bb.width() <= 0 || bb.height() <= 0) {
            return;
        }
        float tw = tailW();
        float left = bb.left + (out ? 0f : tw);
        float right = bb.right - (out ? tw : 0f);
        float top = bb.top;
        float bottom = bb.bottom;
        if (style == STICKER) {
            right -= 3f * d;
            bottom -= 3f * d;
        }
        if (style == SOFT) {
            bottom -= 2f * d;
        }
        body.set(left, top, right, bottom);

        float r = baseRadius();
        float small = Math.min(r, 5f * d);
        int fillOut = k.accent;
        int fillIn = k.inBubble;

        switch (style) {
            case CLASSIC:
                corners(r, small, true);
                fillBody(c, out ? fillOut : fillIn);
                break;

            case TAIL: {
                if (out) {
                    setRad(r, joinPrev ? r : 0f, r, r);
                } else {
                    setRad(joinPrev ? r : 0f, r, r, r);
                }
                roundPath(body);
                int col = out ? fillOut : fillIn;
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(applyAlpha(col));
                c.drawPath(path, paint);
                if (!joinPrev) {
                    float s = out ? 1f : -1f;
                    float ix = out ? body.right : body.left;
                    tail.reset();
                    tail.moveTo(ix, body.top);
                    tail.lineTo(ix + s * tw, body.top);
                    tail.quadTo(ix + s * tw * 0.30f, body.top + tw * 0.55f, ix, body.top + tw * 1.7f);
                    tail.close();
                    c.drawPath(tail, paint);
                }
                break;
            }

            case SPEECH: {
                corners(r, small * 0.8f, true);
                roundPath(body);
                int col = out ? fillOut : fillIn;
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(applyAlpha(col));
                c.drawPath(path, paint);
                if (!joinNext) {
                    float s = out ? 1f : -1f;
                    float ix = out ? body.right : body.left;
                    float by = body.bottom;
                    tail.reset();
                    tail.moveTo(ix - s * 5f * d, by - 11f * d);
                    tail.quadTo(ix, by - 2f * d, ix + s * tw, by);
                    tail.quadTo(ix - s * 2f * d, by + 0.01f, ix - s * 11f * d, by);
                    tail.close();
                    c.drawPath(tail, paint);
                }
                break;
            }

            case CAPSULE:
                setRad(r, r, r, r);
                fillBody(c, out ? fillOut : fillIn);
                break;

            case SHARP:
                setRad(r, r, r, r);
                fillBody(c, out ? fillOut : fillIn);
                break;

            case GRADIENT: {
                corners(r, small, false);
                roundPath(body);
                if (out) {
                    paint.setShader(new LinearGradient(body.left, body.top, body.right, body.bottom,
                        k.accent, k.accent2, Shader.TileMode.CLAMP));
                } else {
                    paint.setShader(new LinearGradient(body.left, body.top, body.right, body.bottom,
                        fillIn, Look.blend(fillIn, k.accent, 0.16f), Shader.TileMode.CLAMP));
                }
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF000000);
                paint.setAlpha(alpha);
                c.drawPath(path, paint);
                paint.setShader(null);
                break;
            }

            case OUTLINE: {
                float sw = 1.6f * d;
                body.inset(sw / 2f, sw / 2f);
                corners(r, small, false);
                roundPath(body);
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(applyAlpha(k.bg));
                c.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(sw);
                paint.setColor(applyAlpha(out ? k.accent : Look.alpha(k.text2, 0xAA)));
                c.drawPath(path, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }

            case NEON: {
                body.inset(3.5f * d, 3.5f * d);
                corners(r, small, false);
                roundPath(body);
                int glow = out ? k.accent : 0xFF22D3EE;
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(applyAlpha(Look.blend(0xFF080B12, glow, 0.10f)));
                c.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(7f * d);
                paint.setColor(applyAlpha(Look.alpha(glow, 34)));
                c.drawPath(path, paint);
                paint.setStrokeWidth(4f * d);
                paint.setColor(applyAlpha(Look.alpha(glow, 80)));
                c.drawPath(path, paint);
                paint.setStrokeWidth(1.6f * d);
                paint.setColor(applyAlpha(Look.lighten(glow, 0.25f)));
                c.drawPath(path, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }

            case SOFT: {
                corners(r, small, false);
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                body.offset(0f, 2f * d);
                roundPath(body);
                paint.setColor(applyAlpha(0x22000000));
                c.drawPath(path, paint);
                body.offset(0f, -1f * d);
                roundPath(body);
                paint.setColor(applyAlpha(0x26000000));
                c.drawPath(path, paint);
                body.offset(0f, -1f * d);
                roundPath(body);
                paint.setColor(applyAlpha(out ? fillOut : fillIn));
                c.drawPath(path, paint);
                break;
            }

            case GLASS: {
                float sw = 1.1f * d;
                body.inset(sw, sw);
                corners(r, small, false);
                roundPath(body);
                int fill = out ? Look.alpha(k.accent, 0xCC) : (k.dark ? 0x40FFFFFF : 0xA0FFFFFF);
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(applyAlpha(fill));
                c.drawPath(path, paint);
                c.save();
                c.clipPath(path);
                paint.setShader(new LinearGradient(0f, body.top, 0f, body.bottom,
                    0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                paint.setColor(0xFF000000);
                paint.setAlpha(alpha);
                c.drawRect(body.left, body.top, body.right, body.top + body.height() * 0.6f, paint);
                paint.setShader(null);
                c.restore();
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(sw);
                paint.setColor(applyAlpha(k.dark ? 0x66FFFFFF : 0xCCFFFFFF));
                c.drawPath(path, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }

            case STICKER: {
                float sw = 2f * d;
                body.inset(sw / 2f, sw / 2f);
                corners(r, small, false);
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                body.offset(3f * d, 3f * d);
                roundPath(body);
                paint.setColor(applyAlpha(0xFF111111));
                c.drawPath(path, paint);
                body.offset(-3f * d, -3f * d);
                roundPath(body);
                paint.setColor(applyAlpha(out ? fillOut : 0xFFFFF3B0));
                c.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(sw);
                paint.setColor(applyAlpha(0xFF111111));
                c.drawPath(path, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }

            case BAR: {
                float s2 = 2f * d;
                if (out) {
                    setRad(r, s2, s2, r);
                } else {
                    setRad(s2, r, r, s2);
                }
                roundPath(body);
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(applyAlpha(out ? Look.blend(k.bg, k.accent, k.dark ? 0.24f : 0.14f) : fillIn));
                c.drawPath(path, paint);
                c.save();
                c.clipPath(path);
                paint.setColor(applyAlpha(out ? k.accent : Look.alpha(k.text2, 0xCC)));
                if (out) {
                    c.drawRect(body.right - 3.5f * d, body.top, body.right, body.bottom, paint);
                } else {
                    c.drawRect(body.left, body.top, body.left + 3.5f * d, body.bottom, paint);
                }
                c.restore();
                break;
            }

            case TERMINAL: {
                float sw = 1.1f * d;
                body.inset(sw / 2f, sw / 2f);
                setRad(r, r, r, r);
                roundPath(body);
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(applyAlpha(0xFF050A06));
                c.drawPath(path, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(sw);
                paint.setColor(applyAlpha(out ? 0xFF39FF7A : 0xFF2D9C58));
                c.drawPath(path, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }

            case LEAF: {
                float sm = Math.min(r, 6f * d);
                if (out) {
                    setRad(r, sm, r, sm);
                } else {
                    setRad(sm, r, sm, r);
                }
                fillBody(c, out ? fillOut : fillIn);
                break;
            }

            case PASTEL: {
                corners(r, small, false);
                roundPath(body);
                int fill = out ? Look.blend(k.bg, k.accent, k.dark ? 0.32f : 0.20f) : fillIn;
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(applyAlpha(fill));
                c.drawPath(path, paint);
                if (out) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1f * d);
                    paint.setColor(applyAlpha(Look.alpha(k.accent, 0x70)));
                    c.drawPath(path, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                break;
            }

            default: { // CHAMFER
                float cut = r;
                float cTl = out ? cut : (joinPrev ? 0f : cut);
                float cTr = out ? (joinPrev ? 0f : cut) : cut;
                float cBr = out ? (joinNext ? 0f : cut) : cut;
                float cBl = out ? cut : (joinNext ? 0f : cut);
                path.reset();
                path.moveTo(body.left + cTl, body.top);
                path.lineTo(body.right - cTr, body.top);
                path.lineTo(body.right, body.top + cTr);
                path.lineTo(body.right, body.bottom - cBr);
                path.lineTo(body.right - cBr, body.bottom);
                path.lineTo(body.left + cBl, body.bottom);
                path.lineTo(body.left, body.bottom - cBl);
                path.lineTo(body.left, body.top + cTl);
                path.close();
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(applyAlpha(out ? fillOut : fillIn));
                c.drawPath(path, paint);
                if (out) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(1f * d);
                    paint.setColor(applyAlpha(Look.lighten(k.accent, 0.35f)));
                    body.inset(2f * d, 2f * d);
                    path.reset();
                    path.moveTo(body.left + cTl, body.top);
                    path.lineTo(body.right - cTr, body.top);
                    c.drawPath(path, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
                break;
            }
        }
    }

    private void fillBody(Canvas c, int color) {
        roundPath(body);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(applyAlpha(color));
        c.drawPath(path, paint);
    }

    private int applyAlpha(int color) {
        if (alpha == 255) {
            return color;
        }
        int a = (Color.alpha(color) * alpha) / 255;
        return (color & 0x00FFFFFF) | (a << 24);
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
