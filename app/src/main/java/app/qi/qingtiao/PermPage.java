package app.qi.qingtiao;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 权限与保活：按 Device.rom() 展示当前机型的 3 步精简引导，完成项自动变薄荷绿底 ✓。 */
final class PermPage extends PageActivity.Page {
    private TextView progressText;
    private TextView progressHint;
    private View[] segs = new View[3];
    private final Step[] steps = new Step[3];
    private final RomProfile rom = Device.rom();

    PermPage(PageActivity a) { super(a); }

    @Override String title() { return "权限与保活"; }

    @Override
    View build() {
        LinearLayout body = scrollPage("当前机型：" + rom.displayName);

        LinearLayout prog = ui.frame(true);
        prog.setBackground(ui.outlined(Ui.CARD, Ui.LINE, 18));
        ui.pad(prog, 18, 14, 18, 16);
        LinearLayout top = ui.frame(false);
        progressText = ui.bold("", 16, Ui.INK);
        ui.fill(top, progressText);
        progressHint = ui.text("", 13, Ui.INK_2);
        top.addView(progressHint);
        prog.addView(top);
        LinearLayout bar = ui.frame(false);
        for (int i = 0; i < 3; i++) {
            segs[i] = new View(a);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ui.dp(6), 1f);
            if (i > 0) sp.leftMargin = ui.dp(6);
            bar.addView(segs[i], sp);
        }
        ui.add(prog, bar, 12);
        ui.add(body, prog, 16, 16, 0);

        LinearLayout g = ui.addGroup(body, null);
        steps[0] = new Step(g, "1", "允许受限设置", rom.restrictedHint, "去设置",
            new Runnable() { @Override public void run() { Device.openAppDetails(a); } });
        steps[1] = new Step(g, "2", "开启无障碍服务", "在无障碍列表找到「轻跳」并打开", "去开启",
            new Runnable() {
                @Override public void run() {
                    scope.enable(true);
                    Device.openAccessibility(a);
                }
            });
        steps[2] = new Step(g, "3", "后台保活", rom.batteryHint, "去配置",
            new Runnable() { @Override public void run() { keepAlive(); } });

        TextView recheck = ui.secondary("重新检测状态", new View.OnClickListener() {
            @Override public void onClick(View v) {
                bind();
                toast("已完成 " + Device.permSteps(a) + " / 3 步");
            }
        });
        ui.add(body, recheck, 18, 16, 0);
        footnote(body, "建议在最近任务里锁定「轻跳」，切后台更稳", 12);
        return root(body);
    }

    private void keepAlive() {
        LinearLayout box = ui.frame(true);
        ui.pad(box, 22, 22, 22, 18);
        box.addView(ui.bold(rom.displayName + " 保活", 18, Ui.INK));
        TextView m = ui.text("① " + rom.batteryHint + "\n② " + rom.autostartHint, 14, Ui.INK_2);
        m.setLineSpacing(0, 1.35f);
        ui.add(box, m, 8);
        final android.app.Dialog[] d = new android.app.Dialog[1];
        TextView bat = ui.primary(Device.batteryIgnored(a) ? "电池优化已忽略 ✓" : "① 忽略电池优化", new View.OnClickListener() {
            @Override public void onClick(View v) {
                d[0].dismiss();
                Device.requestBattery(a);
            }
        });
        ui.add(box, bat, 18);
        TextView auto = ui.secondary("② 打开" + rom.autostartLabel, new View.OnClickListener() {
            @Override public void onClick(View v) {
                d[0].dismiss();
                Device.openAutostart(a);
            }
        });
        ui.add(box, auto, 10);
        d[0] = ui.sheet(box);
        d[0].show();
    }

    private void bind() {
        boolean[] done = {Device.restrictedDone(a), Device.accessibilityOn(a), Device.batteryIgnored(a)};
        int n = 0;
        for (int i = 0; i < 3; i++) {
            steps[i].set(done[i]);
            if (done[i]) n++;
            segs[i].setBackground(ui.shape(done[i] ? Ui.FIELD_ON : Ui.LINE, 3));
        }
        progressText.setText("已完成 " + n + " / 3 步");
        progressHint.setText(n == 3 ? "全部就绪 ✓" : "按顺序设置");
        progressHint.setTextColor(n == 3 ? Ui.TAG_INK : Ui.INK_2);
    }

    @Override
    void onResume() {
        bind();
    }

    private final class Step {
        final TextView num;
        final TextView action;
        final LinearLayout row;
        final String number;
        final String actionText;

        Step(LinearLayout group, String number, String title, String desc, String act, final Runnable go) {
            this.number = number;
            this.actionText = act;
            row = ui.frame(false);
            row.setMinimumHeight(ui.dp(70));
            ui.pad(row, 16, 14, 14, 14);
            num = ui.bold(number, 13, Ui.WARN);
            num.setGravity(Gravity.CENTER);
            FrameLayout nf = new FrameLayout(a);
            nf.addView(num, new FrameLayout.LayoutParams(ui.dp(26), ui.dp(26), Gravity.CENTER));
            ui.fixed(row, nf, 26, 26, 0);
            LinearLayout col = ui.frame(true);
            col.addView(ui.bold(title, 16.5f, Ui.INK));
            TextView d = ui.text(desc, 12.5f, Ui.INK_2);
            d.setLineSpacing(0, 1.25f);
            ui.add(col, d, 3);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cp.leftMargin = ui.dp(12);
            row.addView(col, cp);
            action = ui.pill(act, Ui.INK, Ui.FIELD_MISS, new View.OnClickListener() {
                @Override public void onClick(View v) { go.run(); }
            });
            ui.wrap(row, action, 10);
            group.addView(row);
        }

        void set(boolean done) {
            num.setBackground(ui.oval(done ? Ui.FIELD_ON : Ui.WARN_BG));
            num.setText(done ? "✓" : number);
            num.setTextColor(done ? Ui.INK : Ui.WARN);
            row.setBackgroundColor(done ? Ui.FIELD_SOFT : Ui.CARD);
            action.setText(done ? "已完成" : actionText);
            action.setTextColor(done ? Ui.INK_2 : Ui.FIELD_MISS);
            action.setBackground(done
                ? ui.pressable(ui.outlined(Ui.CARD, Ui.LINE, 18), ui.outlined(0xFFF0F2F1, Ui.LINE, 18))
                : ui.pressable(ui.shape(Ui.INK, 18), ui.shape(0xFF262C28, 18)));
        }
    }
}