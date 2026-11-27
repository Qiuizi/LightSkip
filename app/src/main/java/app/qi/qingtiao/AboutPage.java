package app.qi.qingtiao;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Set;

/** 关于与隐私：「本机说明」规格表，每一行都和程序实际行为一致；版本号；带确认的清除跳过记录。 */
final class AboutPage extends PageActivity.Page {
    private LinearLayout table;

    AboutPage(PageActivity a) { super(a); }

    @Override String title() { return "关于与隐私"; }

    @Override int tint() { return Ui.FIELD_SOFT; }

    @Override
    View build() {
        LinearLayout body = scrollPage("给朋友看：轻跳在你手机上做什么、不做什么。");
        View head = body.getChildAt(0);
        head.setBackground(ui.fieldBg(Ui.FIELD_SOFT));
        ui.pad(head, 20, 4, 20, 26);

        LinearLayout card = ui.frame(true);
        card.setBackground(ui.outlined(Ui.CARD, Ui.LINE, 22));
        card.setClipToOutline(true);
        LinearLayout th = ui.frame(true);
        th.setBackgroundColor(Ui.INK);
        ui.pad(th, 18, 16, 18, 14);
        th.addView(ui.bold("本机说明", 24, Ui.WHITE));
        ui.add(th, ui.text("轻跳 v" + Ui.version(a) + " · 与程序实际行为一致", 12, 0x99FFFFFF), 2);
        card.addView(th);
        table = ui.frame(true);
        android.graphics.drawable.GradientDrawable div = new android.graphics.drawable.GradientDrawable();
        div.setColor(Ui.LINE);
        div.setSize(1, Math.max(1, ui.dp(0.8f)));
        table.setDividerDrawable(new android.graphics.drawable.InsetDrawable(div, ui.dp(18), 0, ui.dp(18), 0));
        table.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        card.addView(table);
        ui.add(body, card, 16, 16, 0);
        footnote(body, "*开屏广告一般 3–5 秒，这里取 3 秒，宁可少算。", 10);

        LinearLayout g = ui.addGroup(body, "关于");
        g.addView(ui.item(Glyph.PHONE, "版本", "自用和朋友小范围使用，不上架",
            ui.value("v" + Ui.version(a)), null).row);
        Ui.Item clear = ui.item(Glyph.TRASH, "清除跳过记录", "今日和累计次数归零，最近跳过记录一起清掉", null,
            new View.OnClickListener() {
                @Override public void onClick(View v) { confirmClear(); }
            });
        clear.title.setTextColor(Ui.DANGER);
        ((android.widget.FrameLayout) clear.icon).setBackground(ui.shape(0xFFFDECEA, 10));
        ((android.widget.ImageView) ((android.widget.FrameLayout) clear.icon).getChildAt(0))
            .setImageDrawable(new Glyph(Glyph.TRASH, Ui.DANGER, ui.dp(1.7f)));
        g.addView(clear.row);
        return root(body);
    }

    private void bindTable() {
        table.removeAllViews();
        table.addView(ui.kv("网络权限", "无", 34, "没申请网络权限，数据传不出去。", Ui.FIELD_ON));
        if (scope.all()) {
            table.addView(ui.kv("生效范围", "全部应用", 20,
                "除受保护的应用外都会识别；可以在「生效范围」里单独关掉某个应用。", 0));
        } else {
            Set<String> picked = scope.selected();
            picked.remove(RuleBook.SELF);
            table.addView(ui.kv("生效范围", "仅勾选的 " + picked.size() + " 个应用", 20,
                "没勾选的应用，轻跳不读取也不点击。", 0));
        }
        table.addView(ui.kv("永不点击", null, 0,
            "微信、QQ、支付宝、云闪付、工商银行、建设银行、系统设置、系统界面、安装器。导入或录制的规则也解锁不了。", 0));
        table.addView(ui.kv("读取内容", null, 0,
            "只看当前窗口里按钮的文字、ID 和位置，用来找「跳过」。不截屏，不保存屏幕内容。", 0));
        table.addView(ui.kv("点击依据", null, 0,
            Ui.builtinAppCount() + " 个应用有专属规则；其余要同时有「跳过」字样和广告标识，几项打分够高才点。", 0));
        table.addView(ui.kv("本机记录", "今天 " + scope.todaySkips() + " 次 · 累计 " + scope.totalSkips() + " 次", 20,
            "计数只存这两个数字；另有最近 500 条跳过明细（哪个应用、哪个按钮）留在本机，用来排查误点。卸载即清空。", 0));
        android.text.SpannableStringBuilder note = new android.text.SpannableStringBuilder("按每次跳过约 3 秒估算");
        int st = note.length();
        note.append("*");
        note.setSpan(new android.text.style.SuperscriptSpan(), st, note.length(), 0);
        note.append("，不是精确计时。");
        table.addView(ui.kv("节省时间", "约 " + Ui.saved(scope.savedSeconds()), 20, note, 0));
    }

    private void confirmClear() {
        ui.confirm("清除跳过记录？", "今日和累计次数会归零，最近 500 条跳过明细也一起删掉。规则和设置不受影响。",
            "清除", true, new Runnable() {
                @Override public void run() {
                    scope.clearStats();
                    final java.io.File dir = a.getFilesDir();
                    new Thread(new Runnable() {
                        @Override public void run() {
                            HitLog log = new HitLog(dir);
                            log.clear();
                            log.close();
                        }
                    }, "clear-hits").start();
                    toast("已清除");
                    bindTable();
                }
            }).show();
    }

    @Override void onResume() { bindTable(); }
}
