package app.qi.qingtiao;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * V5「大开关」设计 token 与原生控件工厂。颜色、字号、圆角、间距全部对齐 design/v5/IMPLEMENT.md 第 1 节。
 * 所有组件都是纯 Java 原生 View，无第三方依赖。
 */
final class Ui {
    // ---- 颜色 token ----
    static final int FIELD_MISS = 0xFFFDDD54;
    static final int FIELD_ON = 0xFF85EDBC;
    static final int FIELD_OFF = 0xFFD5D8D6;
    static final int FIELD_SOFT = 0xFFE6F7EE;
    static final int INK = 0xFF0E1310;
    static final int INK_2 = 0xFF515753;
    static final int INK_3 = 0xFF8A908C;
    static final int BG = 0xFFF9FAFA;
    static final int CARD = 0xFFFFFFFF;
    static final int LINE = 0xFFE2E5E3;
    static final int CHIP = 0xFFE9F1EC;
    static final int SOON = 0xFFECEFED;
    static final int WARN = 0xFF974D00;
    static final int WARN_BG = 0xFFFFF3D6;
    static final int DANGER = 0xFFB42318;
    static final int INK_72 = 0xB80E1310;
    static final int INK_10 = 0x1A0E1310;
    static final int INK_8 = 0x140E1310;
    /** 「专属规则」标签：薄荷底深绿字。 */
    static final int TAG_BG = 0xFFDDF6E9;
    static final int TAG_INK = 0xFF136B45;
    static final int WHITE = 0xFFFFFFFF;

    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface BOLD = Typeface.DEFAULT_BOLD;

    static final DecelerateInterpolator EASE = new DecelerateInterpolator();

    final Activity a;
    private final float density;

    Ui(Activity activity) {
        a = activity;
        density = activity.getResources().getDisplayMetrics().density;
    }

    int dp(float v) {
        return Math.round(density * v);
    }

    // ---- 形状 ----

    GradientDrawable shape(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    GradientDrawable outlined(int fill, int stroke, float radius) {
        GradientDrawable d = shape(fill, radius);
        d.setStroke(dp(1), stroke);
        return d;
    }

    GradientDrawable oval(int fill) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(fill);
        return d;
    }

    /** 首页色场：只有下方两个角圆 32dp。 */
    GradientDrawable fieldBg(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        float r = dp(32);
        d.setCornerRadii(new float[]{0, 0, 0, 0, r, r, r, r});
        return d;
    }

    /** 按压态：底色上叠一层 8% 墨色。 */
    StateListDrawable pressable(GradientDrawable normal, GradientDrawable pressed) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        s.setExitFadeDuration(150);
        return s;
    }

    StateListDrawable pressableColor(int color, float radius) {
        return pressable(shape(color, radius), shape(blend(color, INK, 0.08f), radius));
    }

    StateListDrawable pressableClear() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(INK_8));
        s.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        s.setExitFadeDuration(150);
        return s;
    }

    static int blend(int from, int to, float t) {
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }

    // ---- 布局 ----

    LinearLayout frame(boolean vertical) {
        LinearLayout v = new LinearLayout(a);
        v.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        if (!vertical) v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    void pad(View v, float l, float t, float r, float b) {
        v.setPadding(dp(l), dp(t), dp(r), dp(b));
    }

    /** 竖向追加，左右撑满；margin 单位 dp。 */
    void add(LinearLayout parent, View v, float top) {
        add(parent, v, top, 0, 0);
    }

    void add(LinearLayout parent, View v, float top, float side, float bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(top);
        p.leftMargin = dp(side);
        p.rightMargin = dp(side);
        p.bottomMargin = dp(bottom);
        parent.addView(v, p);
    }

    /** 横向里占满剩余宽度（单层 weight，不嵌套）。 */
    void fill(LinearLayout row, View v) {
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    void wrap(LinearLayout row, View v, float leftMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(leftMargin);
        row.addView(v, p);
    }

    void fixed(LinearLayout row, View v, float w, float h, float leftMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(w), dp(h));
        p.leftMargin = dp(leftMargin);
        row.addView(v, p);
    }

    View space(float h) {
        View v = new View(a);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    // ---- 窗口 ----

    /** 浅色系统栏：状态栏/导航栏用给定颜色，图标深色。 */
    void bars(int status) {
        Window w = a.getWindow();
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(status);
        w.setNavigationBarColor(BG);
        w.getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        w.getDecorView().setBackgroundColor(BG);
    }

    /** Android 15 (targetSdk 35) 强制 edge-to-edge：给顶部栏/底栏叠加系统栏内边距，背景仍铺满状态栏。 */
    static void insetTop(final View target) {
        final int l = target.getPaddingLeft();
        final int t = target.getPaddingTop();
        final int r = target.getPaddingRight();
        final int b = target.getPaddingBottom();
        target.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                int top = android.os.Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(android.view.WindowInsets.Type.statusBars()).top
                    : insets.getSystemWindowInsetTop();
                v.setPadding(l, t + top, r, b);
                return insets;
            }
        });
        target.requestApplyInsets();
    }

    static void insetBottom(final View target) {
        final int l = target.getPaddingLeft();
        final int t = target.getPaddingTop();
        final int r = target.getPaddingRight();
        final int b = target.getPaddingBottom();
        target.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets insets) {
                int bottom = android.os.Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
                    : insets.getSystemWindowInsetBottom();
                v.setPadding(l, t, r, b + bottom);
                return insets;
            }
        });
        target.requestApplyInsets();
    }

    ScrollView scroller() {
        ScrollView s = new ScrollView(a);
        s.setBackgroundColor(BG);
        s.setFillViewport(true);
        s.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        s.setVerticalScrollBarEnabled(false);
        return s;
    }

    // ---- 文字 ----

    TextView text(CharSequence s, float sp, int color) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setIncludeFontPadding(true);
        return t;
    }

    TextView bold(CharSequence s, float sp, int color) {
        TextView t = text(s, sp, color);
        t.setTypeface(BOLD);
        return t;
    }

    TextView medium(CharSequence s, float sp, int color) {
        TextView t = text(s, sp, color);
        t.setTypeface(MEDIUM);
        return t;
    }

    TextView mono(CharSequence s, float sp, int color) {
        TextView t = text(s, sp, color);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    TextView line(TextView t) {
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    TextView pageTitle(String s) {
        TextView t = bold(s, 32, INK);
        t.setLetterSpacing(-0.01f);
        return t;
    }

    TextView pageDesc(String s) {
        TextView t = text(s, 14, INK_2);
        t.setLineSpacing(0, 1.25f);
        return t;
    }

    TextView groupTitle(String s) {
        TextView t = text(s, 12.5f, INK_2);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(18);
        p.bottomMargin = dp(6);
        p.leftMargin = dp(24);
        p.rightMargin = dp(16);
        t.setLayoutParams(p);
        return t;
    }

    TextView badge(String s, int bg, int fg) {
        TextView t = text(s, 11, fg);
        t.setTypeface(MEDIUM);
        t.setBackground(shape(bg, 7));
        pad(t, 7, 2, 7, 2);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    TextView soonTag() {
        return badge("即将推出", SOON, INK_2);
    }

    /** 加粗前半句 + 灰色后半句。 */
    static CharSequence boldThen(String head, String tail, int tailColor) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        b.append(head);
        b.setSpan(new StyleSpan(Typeface.BOLD), 0, head.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new ForegroundColorSpan(INK), 0, head.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int s = b.length();
        b.append(tail);
        b.setSpan(new ForegroundColorSpan(tailColor), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
    }

    /** 在文字末尾追加一段可点击的加粗下划线链接。 */
    static void appendLink(SpannableStringBuilder b, String label, final int color, final Runnable action) {
        int s = b.length();
        b.append(label);
        b.setSpan(new ClickableSpan() {
            @Override public void onClick(View widget) { action.run(); }
            @Override public void updateDrawState(TextPaint ds) {
                ds.setColor(color);
                ds.setUnderlineText(true);
                ds.setFakeBoldText(true);
            }
        }, s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    static void linkify(TextView t) {
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setHighlightColor(Color.TRANSPARENT);
    }

    // ---- 图标 ----

    ImageView glyph(int kind, int color, float sizeDp) {
        ImageView v = new ImageView(a);
        v.setImageDrawable(new Glyph(kind, color, dp(1.7f)));
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    /** 34dp CHIP 圆角底块 + 20dp 线性图标。 */
    FrameLayout iconChip(int kind, int bg, int fg) {
        FrameLayout f = new FrameLayout(a);
        f.setBackground(shape(bg, 8));
        ImageView g = new ImageView(a);
        g.setImageDrawable(new Glyph(kind, fg, dp(1.6f)));
        f.addView(g, new FrameLayout.LayoutParams(dp(15), dp(15), Gravity.CENTER));
        return f;
    }

    // ---- 分组与列表项 ----

    /** 分组卡：白底、1dp LINE 描边、圆角 18；子项之间的分隔线从左侧 60dp 开始。 */
    LinearLayout group() {
        LinearLayout g = frame(true);
        g.setBackground(outlined(CARD, LINE, 18));
        g.setClipToOutline(true);
        GradientDrawable div = new GradientDrawable();
        div.setColor(LINE);
        div.setSize(1, Math.max(1, dp(0.8f)));
        g.setDividerDrawable(new android.graphics.drawable.InsetDrawable(div, dp(52), 0, 0, 0));
        g.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        return g;
    }

    /** 把分组卡加进页面（左右 16dp）。 */
    LinearLayout addGroup(LinearLayout page, String title) {
        if (title != null) page.addView(groupTitle(title));
        LinearLayout g = group();
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = dp(16);
        p.rightMargin = dp(16);
        p.topMargin = title == null ? dp(12) : 0;
        page.addView(g, p);
        return g;
    }

    /** 标准列表项：图标块 + 标题/副文 + 右侧值或标签 + ›。返回整行，值视图通过 tag 取。 */
    Item item(int icon, String title, String desc, View trailing, View.OnClickListener click) {
        Item it = new Item();
        LinearLayout row = frame(false);
        row.setMinimumHeight(dp(56));
        pad(row, 14, 12, 12, 12);
        if (icon >= 0) {
            it.icon = iconChip(icon, CHIP, INK);
            fixed(row, it.icon, 26, 26, 0);
        }
        LinearLayout col = frame(true);
        it.title = line(bold(title, 17, INK));
        col.addView(it.title);
        it.desc = text(desc, 12, INK_2);
        it.desc.setLineSpacing(0, 1.15f);
        if (desc == null || desc.isEmpty()) it.desc.setVisibility(View.GONE);
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp2.topMargin = dp(2);
        col.addView(it.desc, dp2);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = dp(icon >= 0 ? 12 : 0);
        row.addView(col, cp);
        if (trailing != null) wrap(row, trailing, 10);
        it.trailing = trailing;
        if (click != null) {
            ImageView chev = glyph(Glyph.CHEVRON, INK_3, 16);
            LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(dp(16), dp(16));
            chp.leftMargin = dp(6);
            row.addView(chev, chp);
            row.setBackground(pressableClear());
            row.setOnClickListener(click);
        }
        it.row = row;
        return it;
    }

    static final class Item {
        LinearLayout row;
        View icon;
        TextView title;
        TextView desc;
        View trailing;
    }

    TextView value(String s) {
        return text(s, 14.5f, INK_2);
    }

    // ---- 按钮 ----

    /** 主按钮：INK 底白字，最小高 52，圆角 16。 */
    TextView primary(String label, View.OnClickListener click) {
        TextView b = bold(label, 15.5f, WHITE);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(52));
        pad(b, 16, 12, 16, 12);
        b.setBackground(pressable(shape(INK, 16), shape(0xFF2A302C, 16)));
        b.setOnClickListener(click);
        return b;
    }

    /** 次按钮：白底描边。 */
    TextView secondary(String label, View.OnClickListener click) {
        TextView b = bold(label, 15.5f, INK);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(52));
        pad(b, 16, 12, 16, 12);
        b.setBackground(pressable(outlined(CARD, LINE, 16), outlined(0xFFF0F2F1, LINE, 16)));
        b.setOnClickListener(click);
        return b;
    }

    /** 禁用态：SOON 底、INK_3 字。 */
    void enable(TextView b, boolean on, boolean primaryStyle) {
        b.setEnabled(on);
        if (on) {
            b.setTextColor(primaryStyle ? WHITE : INK);
            b.setBackground(primaryStyle
                ? pressable(shape(INK, 16), shape(0xFF2A302C, 16))
                : pressable(outlined(CARD, LINE, 16), outlined(0xFFF0F2F1, LINE, 16)));
        } else {
            b.setTextColor(INK_3);
            b.setBackground(shape(SOON, 16));
        }
    }

    /** 小胶囊动作按钮（任务卡右侧「去设置」）。 */
    TextView pill(String label, int bg, int fg, View.OnClickListener click) {
        TextView b = bold(label, 13.5f, fg);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(36));
        b.setMinWidth(dp(72));
        pad(b, 14, 6, 14, 6);
        b.setBackground(pressable(shape(bg, 18), shape(blend(bg, WHITE, 0.12f), 18)));
        b.setOnClickListener(click);
        return b;
    }

    /** 返回导航栏：44×44 返回键 + 滚动后淡入的标题。 */
    NavBar navBar(String title, final Runnable back) {
        NavBar n = new NavBar();
        LinearLayout bar = frame(false);
        bar.setMinimumHeight(dp(52));
        pad(bar, 8, 4, 16, 4);
        FrameLayout btn = new FrameLayout(a);
        btn.setBackground(pressable(oval(Color.TRANSPARENT), oval(INK_8)));
        ImageView g = new ImageView(a);
        g.setImageDrawable(new Glyph(Glyph.BACK, INK, dp(2f)));
        btn.addView(g, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        btn.setContentDescription("返回");
        btn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { back.run(); }
        });
        fixed(bar, btn, 44, 44, 0);
        n.title = line(bold(title, 16, INK));
        n.title.setAlpha(0f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(4);
        bar.addView(n.title, tp);
        n.bar = bar;
        return n;
    }

    static final class NavBar {
        LinearLayout bar;
        TextView title;
        private boolean shown;

        void reveal(boolean show) {
            if (show == shown) return;
            shown = show;
            title.animate().alpha(show ? 1f : 0f).setDuration(180).setInterpolator(EASE).start();
        }
    }

    /** 模式单选卡：选中 2dp INK 描边，未选中 1dp LINE。 */
    ModeCard modeCard(String title, String tag, String desc) {
        ModeCard m = new ModeCard();
        LinearLayout card = frame(false);
        card.setGravity(Gravity.TOP);
        pad(card, 14, 14, 14, 14);
        m.radio = new View(a);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(20), dp(20));
        rp.topMargin = dp(1);
        card.addView(m.radio, rp);
        LinearLayout col = frame(true);
        LinearLayout head = frame(false);
        head.addView(bold(title, 16.5f, INK));
        if (tag != null) wrap(head, badge(tag, TAG_BG, TAG_INK), 8);
        col.addView(head);
        TextView d = text(desc, 12.5f, INK_2); if (desc == null || desc.isEmpty()) d.setVisibility(View.GONE);
        d.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dp2.topMargin = dp(3);
        col.addView(d, dp2);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = dp(12);
        card.addView(col, cp);
        m.card = card;
        m.ui = this;
        m.set(false);
        return m;
    }

    static final class ModeCard {
        LinearLayout card;
        View radio;
        Ui ui;

        void set(boolean checked) {
            GradientDrawable bg = ui.shape(CARD, 18);
            bg.setStroke(ui.dp(checked ? 2 : 1), checked ? INK : LINE);
            card.setBackground(bg);
            GradientDrawable ring = ui.oval(CARD);
            ring.setStroke(ui.dp(checked ? 2 : 1.5f), checked ? INK : INK_3);
            if (checked) {
                GradientDrawable dot = ui.oval(INK);
                android.graphics.drawable.LayerDrawable l =
                    new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{ring, dot});
                int in = ui.dp(5);
                l.setLayerInset(1, in, in, in, in);
                radio.setBackground(l);
            } else {
                radio.setBackground(ring);
            }
        }
    }

    /** 信任行：22dp 图标 + 前半句加粗。warn 时换成 WARN_BG 圆角 14 底。 */
    TrustRow trustRow(int icon) {
        TrustRow t = new TrustRow();
        LinearLayout row = frame(false);
        row.setGravity(Gravity.CENTER_VERTICAL);
        pad(row, 8, 7, 8, 7);
        t.icon = glyph(icon, INK, 22);
        row.addView(t.icon);
        t.text = text("", 14, INK_2);
        t.text.setLineSpacing(0, 1.2f);
        linkify(t.text);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(12);
        row.addView(t.text, tp);
        t.row = row;
        t.ui = this;
        return t;
    }

    static final class TrustRow {
        LinearLayout row;
        ImageView icon;
        TextView text;
        Ui ui;

        void setWarn(boolean warn) {
            row.setBackground(warn ? ui.shape(WARN_BG, 14) : null);
        }
    }

    /** 规格表的一行：左键名，右侧大值 + 说明。 */
    LinearLayout kv(String key, CharSequence big, float bigSp, CharSequence desc, int bg) {
        LinearLayout row = frame(false);
        row.setGravity(Gravity.TOP);
        pad(row, 18, 16, 18, 16);
        if (bg != 0) row.setBackgroundColor(bg);
        TextView k = bold(key, 13, INK);
        LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT);
        kp.topMargin = dp(big == null ? 1 : bigSp > 24 ? 10 : 4);
        row.addView(k, kp);
        LinearLayout col = frame(true);
        if (big != null) col.addView(bold(big, bigSp, INK));
        if (desc != null) {
            TextView d = text(desc, 13.5f, INK_2);
            d.setLineSpacing(0, 1.3f);
            LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dp2.topMargin = dp(big == null ? 0 : 4);
            col.addView(d, dp2);
        }
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    EditText input(String hint, boolean monospace) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setHintTextColor(INK_3);
        e.setTextColor(INK);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, monospace ? 12.5f : 15);
        if (monospace) e.setTypeface(Typeface.MONOSPACE);
        e.setBackground(outlined(CARD, LINE, 14));
        pad(e, 14, 12, 14, 12);
        return e;
    }

    // ---- 对话框（底部圆角卡） ----

    /** 底部圆角卡对话框，内容自定义。 */
    Dialog sheet(View content) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout wrapBox = new FrameLayout(a);
        pad(wrapBox, 12, 0, 12, 12);
        content.setBackground(shape(CARD, 26));
        wrapBox.addView(content, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setContentView(wrapBox);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.32f);
            w.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
        return d;
    }

    /** 标准确认卡：标题 + 正文 + 「取消」「确认」。danger 时确认键用红色。 */
    Dialog confirm(String title, String message, String ok, boolean danger, final Runnable onOk) {
        LinearLayout box = frame(true);
        pad(box, 22, 22, 22, 18);
        box.addView(bold(title, 18, INK));
        if (message != null) {
            TextView m = text(message, 14, INK_2);
            m.setLineSpacing(0, 1.3f);
            add(box, m, 8);
        }
        LinearLayout btns = frame(false);
        final Dialog[] holder = new Dialog[1];
        TextView cancel = secondary("取消", new View.OnClickListener() {
            @Override public void onClick(View v) { holder[0].dismiss(); }
        });
        TextView yes = primary(ok, new View.OnClickListener() {
            @Override public void onClick(View v) {
                holder[0].dismiss();
                onOk.run();
            }
        });
        if (danger) yes.setBackground(pressable(shape(DANGER, 16), shape(0xFF8F1B12, 16)));
        fill(btns, cancel);
        LinearLayout.LayoutParams yp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        yp.leftMargin = dp(10);
        btns.addView(yes, yp);
        add(box, btns, 20);
        Dialog d = sheet(box);
        holder[0] = d;
        return d;
    }

    // ---- 动效 ----

    /** 色场变色 300ms，并同步状态栏。 */
    static ValueAnimator tint(final View field, final GradientDrawable bg, int from, int to,
            final Runnable onFrame) {
        ValueAnimator va = ValueAnimator.ofArgb(from, to);
        va.setDuration(300);
        va.setInterpolator(EASE);
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator an) {
                bg.setColor((Integer) an.getAnimatedValue());
                field.invalidate();
                if (onFrame != null) onFrame.run();
            }
        });
        va.start();
        return va;
    }

    // ---- 文案工具 ----

    /** 节省时间：m = round(秒/60)；≥60 分写「H 小时 M 分」，否则「M 分钟」（决定 3b）。 */
    static String saved(long seconds) {
        if (seconds <= 0) return "0 分钟";
        if (seconds < 30) return "不到 1 分钟";
        long m = Math.round(seconds / 60.0);
        if (m >= 60) {
            long h = m / 60, mm = m % 60;
            return mm == 0 ? h + " 小时" : h + " 小时 " + mm + " 分";
        }
        return m + " 分钟";
    }

    /** 内置规则里真实应用的数量（排除内置测试页）。 */
    static int builtinAppCount() {
        int n = 0;
        for (RuleBook.Entry e : RuleBook.builtins()) if (!RuleBook.SELF.equals(e.pkg)) n++;
        return n;
    }

    static String version(Activity a) {
        try {
            return a.getPackageManager().getPackageInfo(a.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }
}
