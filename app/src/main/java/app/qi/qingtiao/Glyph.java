package app.qi.qingtiao;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * 线性图标（原型里的 12 个 + 导航用的几个），在 24×24 坐标系里用 Path 手绘，按 bounds 等比缩放。
 * 不引入矢量资源文件，任何尺寸都清晰。
 */
final class Glyph extends Drawable {
    static final int GRID = 0, SEARCH = 1, DASH = 2, DOWNLOAD = 3, RECORD = 4, SHIELD = 5, FLASK = 6,
        INFO = 7, LOCK = 8, NO_NET = 9, PHONE = 10, TRASH = 11, BACK = 12, CHEVRON = 13, CHECK = 14,
        DOTS = 15, LIST = 16, ALERT = 17, PLAY = 18, COPY = 19;

    private final int kind;
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final float strokePx;
    private static final DashPathEffect DASHES = new DashPathEffect(new float[]{2.6f, 2.4f}, 0);

    Glyph(int kind, int color, float strokePx) {
        this.kind = kind;
        this.strokePx = strokePx;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(color);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(color);
    }

    void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        invalidateSelf();
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        if (s <= 0) return;
        int save = c.save();
        c.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
        c.scale(s, s);
        stroke.setStrokeWidth(strokePx / s);
        stroke.setPathEffect(null);
        path.reset();
        switch (kind) {
            case GRID:
                rr(c, 4, 4, 10.5f, 10.5f, 2);
                rr(c, 13.5f, 4, 20, 10.5f, 2);
                rr(c, 4, 13.5f, 10.5f, 20, 2);
                rr(c, 13.5f, 13.5f, 20, 20, 2);
                break;
            case SEARCH:
                c.drawCircle(10.5f, 10.5f, 6f, stroke);
                c.drawLine(15f, 15f, 20f, 20f, stroke);
                break;
            case DASH:
                stroke.setPathEffect(DASHES);
                c.drawCircle(12, 12, 8, stroke);
                stroke.setPathEffect(null);
                c.drawCircle(12, 12, 2.2f, fill);
                break;
            case DOWNLOAD:
                c.drawLine(12, 4, 12, 14.5f, stroke);
                path.moveTo(7.5f, 10.5f); path.lineTo(12, 15); path.lineTo(16.5f, 10.5f);
                c.drawPath(path, stroke);
                path.reset();
                path.moveTo(4.5f, 16); path.lineTo(4.5f, 19.5f); path.lineTo(19.5f, 19.5f); path.lineTo(19.5f, 16);
                c.drawPath(path, stroke);
                break;
            case RECORD:
                c.drawCircle(12, 12, 8, stroke);
                c.drawCircle(12, 12, 3.6f, fill);
                break;
            case SHIELD:
                path.moveTo(12, 3.5f);
                path.lineTo(19, 6.2f);
                path.lineTo(19, 11.5f);
                path.cubicTo(19, 16, 16, 19.2f, 12, 20.5f);
                path.cubicTo(8, 19.2f, 5, 16, 5, 11.5f);
                path.lineTo(5, 6.2f);
                path.close();
                c.drawPath(path, stroke);
                break;
            case FLASK:
                path.moveTo(9.5f, 4); path.lineTo(9.5f, 10); path.lineTo(5, 18.2f);
                path.cubicTo(4.5f, 19.2f, 5.2f, 20, 6.2f, 20); path.lineTo(17.8f, 20);
                path.cubicTo(18.8f, 20, 19.5f, 19.2f, 19, 18.2f); path.lineTo(14.5f, 10); path.lineTo(14.5f, 4);
                c.drawPath(path, stroke);
                c.drawLine(8.5f, 4, 15.5f, 4, stroke);
                c.drawLine(7.2f, 15, 16.8f, 15, stroke);
                break;
            case INFO:
                c.drawCircle(12, 12, 8.5f, stroke);
                c.drawLine(12, 11, 12, 16.5f, stroke);
                c.drawCircle(12, 7.8f, 1.1f, fill);
                break;
            case LOCK:
                rr(c, 5.5f, 10.5f, 18.5f, 20, 2.5f);
                path.moveTo(8.5f, 10.5f); path.lineTo(8.5f, 8);
                path.cubicTo(8.5f, 3.4f, 15.5f, 3.4f, 15.5f, 8); path.lineTo(15.5f, 10.5f);
                c.drawPath(path, stroke);
                break;
            case NO_NET:
                r.set(3, 6, 21, 24);
                c.drawArc(r, 225, 90, false, stroke);
                r.set(6.5f, 9.5f, 17.5f, 20.5f);
                c.drawArc(r, 225, 90, false, stroke);
                c.drawCircle(12, 17.6f, 1.3f, fill);
                c.drawLine(4.5f, 4.5f, 19.5f, 19.5f, stroke);
                break;
            case PHONE:
                rr(c, 7, 3.5f, 17, 20.5f, 2.5f);
                c.drawLine(10.5f, 17.5f, 13.5f, 17.5f, stroke);
                break;
            case TRASH:
                c.drawLine(4.5f, 7, 19.5f, 7, stroke);
                path.moveTo(9.5f, 7); path.lineTo(9.5f, 4.5f); path.lineTo(14.5f, 4.5f); path.lineTo(14.5f, 7);
                c.drawPath(path, stroke);
                path.reset();
                path.moveTo(6.5f, 7); path.lineTo(7.5f, 20); path.lineTo(16.5f, 20); path.lineTo(17.5f, 7);
                c.drawPath(path, stroke);
                c.drawLine(10.5f, 11, 10.5f, 16.5f, stroke);
                c.drawLine(13.5f, 11, 13.5f, 16.5f, stroke);
                break;
            case BACK:
                path.moveTo(15, 5); path.lineTo(8, 12); path.lineTo(15, 19);
                c.drawPath(path, stroke);
                break;
            case CHEVRON:
                path.moveTo(9.5f, 6); path.lineTo(15.5f, 12); path.lineTo(9.5f, 18);
                c.drawPath(path, stroke);
                break;
            case CHECK:
                path.moveTo(5, 12.5f); path.lineTo(10, 17.5f); path.lineTo(19, 7);
                c.drawPath(path, stroke);
                break;
            case DOTS:
                c.drawCircle(6, 12, 1.7f, fill);
                c.drawCircle(12, 12, 1.7f, fill);
                c.drawCircle(18, 12, 1.7f, fill);
                break;
            case LIST:
                c.drawLine(9, 7, 19.5f, 7, stroke);
                c.drawLine(9, 12, 19.5f, 12, stroke);
                c.drawLine(9, 17, 19.5f, 17, stroke);
                c.drawCircle(5, 7, 1.2f, fill);
                c.drawCircle(5, 12, 1.2f, fill);
                c.drawCircle(5, 17, 1.2f, fill);
                break;
            case ALERT:
                c.drawCircle(12, 12, 8.5f, stroke);
                c.drawLine(12, 7.5f, 12, 13, stroke);
                c.drawCircle(12, 16.3f, 1.1f, fill);
                break;
            case PLAY:
                rr(c, 4, 5, 20, 19, 3);
                path.moveTo(10.5f, 9.2f); path.lineTo(15, 12); path.lineTo(10.5f, 14.8f); path.close();
                c.drawPath(path, fill);
                break;
            case COPY:
                rr(c, 8.5f, 8.5f, 19.5f, 19.5f, 2.2f);
                path.moveTo(15.5f, 8.5f); path.lineTo(15.5f, 6.5f);
                path.cubicTo(15.5f, 5.3f, 14.7f, 4.5f, 13.5f, 4.5f); path.lineTo(6.5f, 4.5f);
                path.cubicTo(5.3f, 4.5f, 4.5f, 5.3f, 4.5f, 6.5f); path.lineTo(4.5f, 13.5f);
                path.cubicTo(4.5f, 14.7f, 5.3f, 15.5f, 6.5f, 15.5f); path.lineTo(8.5f, 15.5f);
                c.drawPath(path, stroke);
                break;
            default:
                break;
        }
        c.restoreToCount(save);
    }

    private void rr(Canvas c, float l, float t, float rt, float b, float rad) {
        r.set(l, t, rt, b);
        c.drawRoundRect(r, rad, rad, stroke);
    }

    @Override public void setAlpha(int alpha) {
        stroke.setAlpha(alpha);
        fill.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter cf) {
        stroke.setColorFilter(cf);
        fill.setColorFilter(cf);
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    @Override public int getIntrinsicWidth() { return -1; }
    @Override public int getIntrinsicHeight() { return -1; }
}
