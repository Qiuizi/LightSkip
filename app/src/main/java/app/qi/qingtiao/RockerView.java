package app.qi.qingtiao;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 首页实体拨杆：高 120dp、圆角 60、内边距 8、滑块直径 104。
 * 开：INK 底，滑块在右，左侧「正在跳」薄荷绿字；关：10% 墨色底，滑块在左，右侧「已暂停」。
 * 支持点按切换和左右拖动，200ms 减速动画；整件自绘，不嵌套子 View。
 */
final class RockerView extends View {
    interface OnToggle { void onToggle(boolean on); }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knob = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 手画的柔和投影（不用 setShadowLayer，避免软件层拖慢动画）。 */
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final int touchSlop;

    private boolean checked;
    /** 0 = 关（左），1 = 开（右）。 */
    private float pos;
    private ValueAnimator anim;
    private OnToggle listener;

    private float downX;
    private float downY;
    private float downPos;
    private boolean dragging;

    RockerView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        knob.setColor(Ui.BG);
        shadow.setColor(0x1A000000);
        knobText.setColor(Ui.INK);
        knobText.setTypeface(Typeface.DEFAULT_BOLD);
        knobText.setTextAlign(Paint.Align.CENTER);
        knobText.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 30,
            c.getResources().getDisplayMetrics()));
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16,
            c.getResources().getDisplayMetrics()));
        setClickable(true);
        setFocusable(true);
        setAccessibilityDelegate(new AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Switch");
                info.setCheckable(true);
                info.setChecked(checked);
            }

            @Override public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                    userSet(!checked);
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
        updateDescription();
    }

    void setOnToggle(OnToggle l) { listener = l; }

    boolean isChecked() { return checked; }

    /** 外部同步状态（不回调）。 */
    void setChecked(boolean on, boolean animate) {
        if (on == checked && anim == null && pos == (on ? 1f : 0f)) return;
        checked = on;
        updateDescription();
        animateTo(on ? 1f : 0f, animate);
    }

    private void userSet(boolean on) {
        boolean changed = on != checked;
        checked = on;
        updateDescription();
        animateTo(on ? 1f : 0f, true);
        if (changed) {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (listener != null) listener.onToggle(on);
        }
    }

    private void updateDescription() {
        setContentDescription(checked ? "自动跳过：开，正在跳" : "自动跳过：关，已暂停");
    }

    private void animateTo(float target, boolean animate) {
        if (anim != null) anim.cancel();
        anim = null;
        if (!animate || !isLaidOut()) {
            pos = target;
            invalidate();
            return;
        }
        ValueAnimator va = ValueAnimator.ofFloat(pos, target);
        va.setDuration(Math.max(80, (long) (200 * Math.abs(target - pos))));
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

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, Math.round(120 * density));
    }

    private float travel() {
        float pad = 8 * density, d = 104 * density;
        return Math.max(1, getWidth() - pad * 2 - d);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float pad = 8 * density, d = 104 * density, r = 60 * density;
        // 轨道：关=10% 墨，开=INK，中间按位置混色
        track.setColor(Ui.blend(Ui.INK_10, Ui.INK, pos));
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, r, r, track);

        // 两侧标签交替淡入
        float cy = h / 2f - (label.descent() + label.ascent()) / 2f;
        label.setTextAlign(Paint.Align.LEFT);
        label.setColor(Ui.FIELD_ON);
        label.setAlpha(Math.round(255 * clamp((pos - 0.4f) / 0.6f)));
        c.drawText("正在跳", 36 * density, cy, label);
        label.setTextAlign(Paint.Align.RIGHT);
        label.setColor(Ui.INK_2);
        label.setAlpha(Math.round(255 * clamp((0.6f - pos) / 0.6f)));
        c.drawText("已暂停", w - 36 * density, cy, label);

        // 滑块
        float cx = pad + d / 2f + travel() * pos;
        shadow.setAlpha(18);
        c.drawCircle(cx, h / 2f + 3 * density, d / 2f + 2.5f * density, shadow);
        shadow.setAlpha(26);
        c.drawCircle(cx, h / 2f + 1.5f * density, d / 2f + 0.8f * density, shadow);
        c.drawCircle(cx, h / 2f, d / 2f, knob);
        float ty = h / 2f - (knobText.descent() + knobText.ascent()) / 2f;
        c.drawText(pos >= 0.5f ? "开" : "关", cx, ty, knobText);
    }

    private static float clamp(float v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                downPos = pos;
                dragging = false;
                setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!dragging && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    // 确认是横向拖动才拦住外层 ScrollView；竖着划照常滚动页面
                    dragging = true;
                    if (anim != null) anim.cancel();
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    pos = clamp(downPos + dx / travel());
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                setPressed(false);
                getParent().requestDisallowInterceptTouchEvent(false);
                if (dragging) userSet(pos >= 0.5f);
                else userSet(!checked);
                return true;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                animateTo(checked ? 1f : 0f, true);
                return true;
            default:
                return true;
        }
    }
}
