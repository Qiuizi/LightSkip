package app.qi.qingtiao;

import android.graphics.DashPathEffect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 通用识别模式：即将推出的说明页。 */
final class GenericPage extends PageActivity.Page {
    GenericPage(PageActivity a) { super(a); }

    @Override String title() { return "通用识别模式"; }

    @Override
    View build() {
        LinearLayout body = scrollPage("给没有专属规则的应用用。");
        LinearLayout hero = ui.frame(true);
        hero.setBackground(ui.shape(Ui.SOON, 22));
        ui.pad(hero, 16, 16, 16, 16);
        TextView tag = ui.badge("即将推出", Ui.INK, Ui.WHITE);
        hero.addView(tag, new LinearLayout.LayoutParams(-2, -2));
        ui.add(hero, ui.bold("不看字也能认出跳过按钮", 18, Ui.INK), 10);
        TextView d = ui.text("按位置、倒计时和大小一起判断，三项都对上才会点。", 13, Ui.INK_2);
        ui.add(hero, d, 4);

        FrameLayout mock = new FrameLayout(a);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFFF7C9D6, 0xFFFBE3B8});
        g.setCornerRadius(ui.dp(14));
        mock.setBackground(g);
        TextView skip = ui.medium("跳过 3", 12, Ui.INK);
        GradientDrawable pill = ui.shape(0x33FFFFFF, 14);
        pill.setStroke(ui.dp(1.5f), Ui.INK, ui.dp(4), ui.dp(3));
        skip.setBackground(pill);
        ui.pad(skip, 10, 4, 10, 4);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        sp.topMargin = ui.dp(12);
        sp.rightMargin = ui.dp(12);
        mock.addView(skip, sp);
        TextView ad = ui.badge("广告", 0xCCFFFFFF, Ui.INK_2);
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        ap.leftMargin = ui.dp(12);
        ap.bottomMargin = ui.dp(12);
        mock.addView(ad, ap);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, ui.dp(128));
        mp.topMargin = ui.dp(14);
        hero.addView(mock, mp);
        ui.add(body, hero, 18, 16, 0);

        LinearLayout grp = ui.addGroup(body, null);
        grp.addView(ui.item(-1, "位置", "右上角或右下角的小按钮。", null, null).row);
        grp.addView(ui.item(-1, "倒计时", "按钮里的数字在 1–5 之间，并且每秒都在变。", null, null).row);
        grp.addView(ui.item(-1, "只在勾选的应用里", "和现在一样，受保护的应用永远排除。上线前会先用测试页反复测。", null, null).row);

        TextView off = ui.primary("启用通用识别 · 即将推出", null);
        ui.enable(off, false, true);
        ui.add(body, off, 18, 16, 0);
        ui.add(body, ui.secondary("先去看看测试页", new View.OnClickListener() {
            @Override public void onClick(View v) { Device.page(a, PageActivity.LAB); }
        }), 10, 16, 0);
        return root(body);
    }
}
