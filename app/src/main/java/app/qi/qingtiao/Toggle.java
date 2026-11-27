package app.qi.qingtiao;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 列表行右侧的小开关：46×28dp，开=INK 轨道，关=LINE 轨道，白色滑块，200ms 动画。
 * 自绘单 View，放进 ListView 行里比系统 Switch 轻得多，也不会带出默认 Material 样式。
 */
final class Toggle extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private boolean checked;
    private float pos;
    private ValueAnimator anim;

    Toggle(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        knob.setColor(Ui.WHITE);
        setClickable(true);
        setFocusable(true);
    }

    boolean isChecked() { return checked; }

    void setChecked(boolean on, boolean animate) {
        checked = on;
        if (anim != null) anim.cancel();
        anim = null;
        float target = on ? 1f : 0f;
        if (!animate || !isLaidOut() || pos == target) {
            pos = target;
            invalidate();
        } else {
            ValueAnimator va = ValueAnimator.ofFloat(pos, target);
            va.setDuration(200);
            va.setInterpolator(Ui.EASE);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    pos = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            anim = va;
            va.start();
        }
        setContentDescription(on ? "已打开" : "已关闭");
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checked);
    }

    @Override
    protected void onMeasure(int w, int h) {
        // 触控区 56×44，绘制区 46×28 居中
        setMeasuredDimension(Math.round(52 * density), Math.round(44 * density));
    }

    @Override
    protected void onDraw(Canvas c) {
        float tw = 40 * density, th = 24 * density;
        float l = (getWidth() - tw) / 2f, t = (getHeight() - th) / 2f;
        track.setColor(Ui.blend(Ui.LINE, Ui.INK, pos));
        rect.set(l, t, l + tw, t + th);
        c.drawRoundRect(rect, th / 2f, th / 2f, track);
        float inset = 2.5f * density, kd = th - inset * 2;
        float cx = l + inset + kd / 2f + (tw - inset * 2 - kd) * pos;
        c.drawCircle(cx, t + th / 2f, kd / 2f, knob);
    }
}
