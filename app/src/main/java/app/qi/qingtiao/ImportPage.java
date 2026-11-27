package app.qi.qingtiao;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入规则：等宽输入框 + 填入示例 + 校验并保存（与已有规则按包名合并，不会冲掉录制的规则）
 * + 已导入列表（单条删除）+ 带确认的清空。格式错误在输入框下方红字提示。
 */
final class ImportPage extends PageActivity.Page {
    private static final String SAMPLE =
        "[{\"pkg\":\"com.example.app\",\"title\":\"示例\",\"ids\":[\"skip_btn\"]}]";

    private EditText input;
    private TextView error;
    private LinearLayout listGroup;
    private TextView clearBtn;

    ImportPage(PageActivity a) { super(a); }

    @Override String title() { return "导入规则"; }

    @Override
    View build() {
        a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        LinearLayout body = scrollPage("粘贴朋友发来的 JSON，不用重装就能支持新应用。");

        LinearLayout warn = ui.frame(false);
        warn.setGravity(Gravity.TOP);
        warn.setBackground(ui.shape(Ui.WARN_BG, 16));
        ui.pad(warn, 14, 12, 14, 12);
        warn.addView(ui.glyph(Glyph.ALERT, Ui.WARN, 18));
        TextView wt = ui.text(Ui.boldThen("规则只能指定点哪个按钮。",
            "它不能联网，也解锁不了受保护的应用。只导入你信任的人给的规则。", Ui.WARN), 13, Ui.WARN);
        wt.setLineSpacing(0, 1.3f);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wp.leftMargin = ui.dp(10);
        warn.addView(wt, wp);
        ui.add(body, warn, 16, 16, 0);

        body.addView(ui.groupTitle("规则内容"));
        input = ui.input(SAMPLE, true);
        input.setMinLines(6);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setHorizontallyScrolling(false);
        ui.add(body, input, 0, 16, 0);
        error = ui.text("", 12.5f, Ui.DANGER);
        error.setVisibility(View.GONE);
        ui.add(body, error, 6, 24, 0);

        LinearLayout btns = ui.frame(false);
        TextView sample = ui.secondary("填入示例", new View.OnClickListener() {
            @Override public void onClick(View v) {
                input.setText(SAMPLE);
                input.setSelection(input.length());
                error.setVisibility(View.GONE);
            }
        });
        TextView save = ui.primary("保存并生效", new View.OnClickListener() {
            @Override public void onClick(View v) { save(); }
        });
        ui.fill(btns, sample);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sp.leftMargin = ui.dp(10);
        btns.addView(save, sp);
        ui.add(body, btns, 12, 16, 0);

        listGroup = ui.addGroup(body, "已导入");
        clearBtn = ui.bold("清空已导入的规则", 14, Ui.DANGER);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setMinHeight(ui.dp(48));
        clearBtn.setBackground(ui.pressableColor(0x00FFFFFF, 14));
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ui.confirm("清空全部自定义规则？", "导入和录制的规则都会删掉，应用会回到内置规则或智能识别。",
                    "清空", true, new Runnable() {
                        @Override public void run() {
                            scope.saveCustomRules("");
                            toast("已清空");
                            bindList();
                        }
                    }).show();
            }
        });
        ui.add(body, clearBtn, 10, 16, 0);
        return root(body);
    }

    private void fail(String msg) {
        error.setText(msg);
        error.setVisibility(View.VISIBLE);
    }

    private void save() {
        String raw = input.getText().toString().trim();
        if (raw.isEmpty()) {
            fail("先粘贴规则内容，或点「填入示例」看看格式。");
            return;
        }
        Map<String, RuleBook.Entry> parsed = RuleBook.parseCustom(raw);
        if (parsed == null) {
            fail("格式不对：要是一个 JSON 数组，形如 [{\"pkg\":\"包名\",\"title\":\"名字\",\"ids\":[\"按钮ID\"]}]");
            return;
        }
        int items = 0;
        try {
            Object root = Json.parse(raw);
            if (root instanceof List) items = ((List<?>) root).size();
        } catch (RuntimeException ignored) { }
        for (RuleBook.Entry e : parsed.values()) {
            if (e.ids.isEmpty()) {
                fail("「" + e.pkg + "」缺少 ids：至少要写一个按钮 ID。");
                return;
            }
        }
        if (parsed.isEmpty()) {
            fail("没有可导入的规则。受保护的应用（微信、支付宝、银行等）会被忽略。");
            return;
        }
        Map<String, RuleBook.Entry> merged = RuleBook.parseCustom(scope.customRules());
        boolean reset = merged == null;
        if (reset) merged = new LinkedHashMap<String, RuleBook.Entry>();
        for (RuleBook.Entry e : parsed.values()) {
            merged.remove(e.pkg);
            merged.put(e.pkg, e);
        }
        scope.saveCustomRules(RuleBook.toJson(merged.values()));
        error.setVisibility(View.GONE);
        input.setText("");
        int skipped = Math.max(0, items - parsed.size());
        toast("已导入 " + parsed.size() + " 条" + (skipped > 0 ? "，忽略 " + skipped + " 条（受保护或重复）" : "")
            + (reset ? "。原有规则已损坏，已被替换" : ""));
        bindList();
    }

    private void bindList() {
        listGroup.removeAllViews();
        List<RuleBook.Entry> list = RuleBook.customEntries();
        clearBtn.setVisibility(list.isEmpty() ? View.GONE : View.VISIBLE);
        if (list.isEmpty()) {
            LinearLayout empty = ui.frame(true);
            empty.setGravity(Gravity.CENTER);
            ui.pad(empty, 16, 22, 16, 22);
            TextView t = ui.bold("还没有导入的规则", 15, Ui.INK);
            t.setGravity(Gravity.CENTER);
            empty.addView(t);
            TextView d = ui.text("保存后会出现在这里。", 13, Ui.INK_2);
            d.setGravity(Gravity.CENTER);
            ui.add(empty, d, 4);
            listGroup.addView(empty);
            return;
        }
        for (final RuleBook.Entry e : list) {
            FrameLayout trash = new FrameLayout(a);
            trash.setBackground(ui.pressable(ui.oval(0x00FFFFFF), ui.oval(Ui.INK_8)));
            ImageView g = ui.glyph(Glyph.TRASH, Ui.DANGER, 20);
            trash.addView(g, new FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER));
            trash.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
            trash.setContentDescription("删除");
            trash.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    ui.confirm("删除「" + e.title + "」？", null, "删除", true, new Runnable() {
                        @Override public void run() {
                            if (!scope.removeCustomRule(e.pkg)) toast("规则格式有误，请直接清空");
                            bindList();
                        }
                    }).show();
                }
            });
            Ui.Item it = ui.item(Glyph.DOWNLOAD, e.title, e.pkg, trash, null);
            it.desc.setTypeface(android.graphics.Typeface.MONOSPACE);
            it.desc.setTextSize(11);
            listGroup.addView(it.row);
        }
    }

    @Override void onResume() { bindList(); }
}
