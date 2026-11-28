package app.qi.qingtiao;

import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 更多：导入规则、最近跳过记录、自带测试页。首页不再出现这些工具词。 */
final class LabPage extends PageActivity.Page {
    private TextView importCount;

    LabPage(PageActivity a) { super(a); }

    @Override String title() { return "更多"; }

    @Override
    View build() {
        LinearLayout body = scrollPage("导入自定义规则、查看最近跳过记录，或用内置测试页体验");
        LinearLayout g1 = ui.addGroup(body, "规则与记录");
        importCount = ui.value("0 条");
        g1.addView(ui.item(Glyph.DOWNLOAD, "导入规则", "粘贴 JSON 规则，免重装扩展", importCount,
            go(PageActivity.IMPORT)).row);
        g1.addView(ui.item(Glyph.LIST, "最近跳过记录", "仅留在本机，最多保存最近 500 条", ui.value("查看"),
            go(PageActivity.HITS)).row);

        LinearLayout g2 = ui.addGroup(body, "自带测试页");
        g2.addView(ui.item(Glyph.PLAY, "模拟开屏广告", "5 秒倒计时广告，看轻跳能不能点掉", ui.value("打开"),
            demo(false)).row);
        g2.addView(ui.item(Glyph.SHIELD, "误触安全测试", "按钮也叫「跳过」但没有广告标识，应当不点", ui.value("打开"),
            demo(true)).row);
        footnote(body, "测试页的点击会计入「今日跳过」，方便确认服务确实在工作。测试页要在生效范围里保持打开（「内置测试页」）。", 12);
        return root(body);
    }

    private View.OnClickListener go(final String page) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) { Device.page(a, page); }
        };
    }

    private View.OnClickListener demo(final boolean guide) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) {
                a.startActivity(new Intent(a, guide ? GuideActivity.class : DemoActivity.class));
            }
        };
    }

    @Override void onResume() {
        importCount.setText(RuleBook.customEntries().size() + " 条");
    }
}
