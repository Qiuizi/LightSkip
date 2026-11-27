package app.qi.qingtiao;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 智能 + 内置规则：识别机制说明 + 25 个常用应用的专属规则（本机已安装的排前面）。 */
final class BuiltinPage extends PageActivity.Page implements AppCatalog.Listener {
    private LinearLayout listGroup;
    private TextView listTitle;

    BuiltinPage(PageActivity a) { super(a); }

    @Override String title() { return "智能 + 内置规则"; }

    @Override
    View build() {
        LinearLayout body = scrollPage("当前的识别方式。先查专属规则，没有规则时才认字样。");
        SpannableStringBuilder d1 = new SpannableStringBuilder("例如哔哩哔哩认 ");
        mono(d1, "count_down");
        d1.append("、");
        mono(d1, "skip");
        d1.append("，位置变了也能找到。录制或导入的规则也按这个方式点。");
        ui.add(body, explain("有专属规则：按按钮 ID 点", d1), 18, 16, 0);
        ui.add(body, explain("没有规则：认字样，并且必须有广告标识",
            "按钮写着「跳过」「跳过广告」「5 秒跳过」等，同一屏还要有「广告」标识，几项一起打分够高才会点。只有「跳过」两个字不会点，防止误触引导页。"),
            10, 16, 0);
        listTitle = ui.groupTitle("");
        body.addView(listTitle);
        listGroup = ui.group();
        ui.add(body, listGroup, 0, 16, 0);
        footnote(body, "已验证 = 在真机上跑过、确认没有误点。其余规则来自公开的常见按钮 ID，可能随应用更新失效，失效时会自动退回智能识别。", 12);
        return root(body);
    }

    private void mono(SpannableStringBuilder b, String s) {
        int st = b.length();
        b.append(s);
        b.setSpan(new TypefaceSpan("monospace"), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new android.text.style.BackgroundColorSpan(Ui.CHIP), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private View explain(String title, CharSequence desc) {
        LinearLayout c = ui.frame(true);
        c.setBackground(ui.outlined(Ui.CARD, Ui.LINE, 18));
        ui.pad(c, 16, 14, 16, 16);
        c.addView(ui.bold(title, 15, Ui.INK));
        TextView d = ui.text(desc, 13, Ui.INK_2);
        d.setLineSpacing(0, 1.35f);
        ui.add(c, d, 6);
        return c;
    }

    private void bindList() {
        listGroup.removeAllViews(); // 固定 25 行，不随滚动增长
        Set<String> installed = catalog.installedPackages();
        List<RuleBook.Entry> have = new ArrayList<RuleBook.Entry>();
        List<RuleBook.Entry> miss = new ArrayList<RuleBook.Entry>();
        for (RuleBook.Entry e : RuleBook.builtins()) {
            if (RuleBook.SELF.equals(e.pkg)) continue;
            if (installed.contains(e.pkg)) have.add(e); else miss.add(e);
        }
        listTitle.setText((have.size() + miss.size()) + " 个专属规则 · 本机已装 " + have.size() + " 个");
        int px = ui.dp(AppIcon.SIZE_DP);
        for (int pass = 0; pass < 2; pass++) {
            for (RuleBook.Entry e : pass == 0 ? have : miss) {
                boolean on = pass == 0;
                LinearLayout row = ui.frame(false);
                row.setMinimumHeight(ui.dp(60));
                ui.pad(row, 14, 10, 16, 10);
                FrameLayout iconBox = new FrameLayout(a);
                if (on) {
                    ImageView icon = AppIcon.create(ui);
                    iconBox.addView(icon, new FrameLayout.LayoutParams(px, px));
                    AppIcon.bind(icon, e.pkg, catalog, px, false);
                } else {
                    TextView letter = ui.bold(e.title.substring(0, 1), 14, Ui.INK_3);
                    letter.setGravity(Gravity.CENTER);
                    letter.setBackground(ui.shape(Ui.SOON, 10));
                    iconBox.addView(letter, new FrameLayout.LayoutParams(px, px));
                }
                ui.fixed(row, iconBox, AppIcon.SIZE_DP, AppIcon.SIZE_DP, 0);
                LinearLayout col = ui.frame(true);
                LinearLayout head = ui.frame(false);
                head.addView(ui.line(ui.medium(e.title, 15, on ? Ui.INK : Ui.INK_2)));
                if (e.verified) ui.wrap(head, ui.badge("已验证", Ui.TAG_BG, Ui.TAG_INK), 6);
                if (RuleBook.hasCustomRule(e.pkg)) ui.wrap(head, ui.badge("已被你的规则覆盖", Ui.SOON, Ui.INK_2), 6);
                col.addView(head);
                StringBuilder ids = new StringBuilder();
                for (String id : new TreeSet<String>(e.ids)) {
                    if (ids.length() > 0) ids.append(" · ");
                    ids.append(id);
                }
                ui.add(col, ui.line(ui.mono(ids.toString(), 11, Ui.INK_3)), 2);
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                cp.leftMargin = ui.dp(14);
                row.addView(col, cp);
                TextView st = on ? ui.bold("已安装", 12.5f, Ui.INK) : ui.text("未安装", 12.5f, Ui.INK_3);
                ui.wrap(row, st, 8);
                listGroup.addView(row);
            }
        }
    }

    @Override void onResume() {
        catalog.addListener(this);
        bindList();
    }

    @Override void onPause() { catalog.removeListener(this); }

    @Override public void onCatalogChanged() { bindList(); }
}
