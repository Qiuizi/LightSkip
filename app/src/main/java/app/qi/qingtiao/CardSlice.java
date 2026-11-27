package app.qi.qingtiao;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * ListView 行背景：把一列独立的行画成一张「分组卡」——首行圆上角、末行圆下角、两侧 1dp 描边，
 * 行与行之间的分隔线从左侧 dividerInset 开始。行被复用时只改 position 并 invalidate，零分配。
 */
final class CardSlice extends Drawable {
    static final int MIDDLE = 0, FIRST = 1, LAST = 2, SINGLE = 3;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint divider = new Paint();
    private final Path shape = new Path();
    private final Path edge = new Path();
    private final RectF arc = new RectF();
    private final float radius;
    private final float strokePx;
    private final float dividerInset;
    private int position = SINGLE;
    private boolean dirty = true;

    CardSlice(float radius, float strokePx, float dividerInset) {
        this.radius = radius;
        this.strokePx = strokePx;
        this.dividerInset = dividerInset;
        fill.setColor(Ui.CARD);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokePx);
        stroke.setColor(Ui.LINE);
        divider.setColor(Ui.LINE);
    }

    static int positionOf(int index, int count) {
        if (count <= 1) return SINGLE;
        if (index == 0) return FIRST;
        if (index == count - 1) return LAST;
        return MIDDLE;
    }

    void setPosition(int p) {
        if (p == position) return;
        position = p;
        dirty = true;
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        dirty = true;
    }

    private void rebuild() {
        Rect b = getBounds();
        float h = strokePx / 2f;
        float l = b.left + h, t = b.top + h, r = b.right - h, bt = b.bottom - h;
        boolean top = position == FIRST || position == SINGLE;
        boolean bottom = position == LAST || position == SINGLE;
        float rt = top ? radius : 0, rb = bottom ? radius : 0;
        shape.reset();
        shape.addRoundRect(new RectF(b.left, b.top, b.right, b.bottom),
            new float[]{rt, rt, rt, rt, rb, rb, rb, rb}, Path.Direction.CW);
        edge.reset();
        // 左边：从下往上
        if (bottom) {
            edge.moveTo(l + rb, bt);
            arc.set(l, bt - 2 * rb, l + 2 * rb, bt);
            edge.arcTo(arc, 90, 90, false);
        } else {
            edge.moveTo(l, b.bottom);
        }
        if (top) {
            edge.lineTo(l, t + rt);
            arc.set(l, t, l + 2 * rt, t + 2 * rt);
            edge.arcTo(arc, 180, 90, false);
            edge.lineTo(r - rt, t);
            arc.set(r - 2 * rt, t, r, t + 2 * rt);
            edge.arcTo(arc, 270, 90, false);
        } else {
            edge.lineTo(l, b.top);
            edge.moveTo(r, b.top);
        }
        if (bottom) {
            edge.lineTo(r, bt - rb);
            arc.set(r - 2 * rb, bt - 2 * rb, r, bt);
            edge.arcTo(arc, 0, 90, false);
            edge.lineTo(l + rb, bt);
        } else {
            edge.lineTo(r, b.bottom);
        }
        dirty = false;
    }

    @Override
    public void draw(Canvas c) {
        if (dirty) rebuild();
        c.drawPath(shape, fill);
        c.drawPath(edge, stroke);
        if (position == FIRST || position == MIDDLE) {
            Rect b = getBounds();
            float y = b.bottom - Math.max(1f, strokePx * 0.8f);
            c.drawRect(b.left + dividerInset, y, b.right - strokePx, b.bottom, divider);
        }
    }

    @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); stroke.setAlpha(alpha); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); stroke.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
