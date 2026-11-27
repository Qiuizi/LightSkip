package app.qi.qingtiao;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 自带测试页。guide=false：模拟开屏广告（带「广告」标识 + 跳过倒计时），轻跳应当自动点掉；
 * GuideActivity（子类）：误触安全测试——按钮也叫「跳过」，但不是内置规则的专属页、没有按钮 ID、
 * 没有广告标识，只拿到「跳过」字样的 40 分，低于 60 分点击线，应当记为「险些点击」而不点。
 * DemoActivity 的类名和按钮 ID（R.id.demo_skip）是内置规则的匹配依据，不能改。
 */
public class DemoActivity extends Activity {
    private Ui ui;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView skip;
    private int left = 5;
    private boolean guide;

    private final Runnable countdown = new Runnable() {
        @Override public void run() {
            if (left > 1) {
                left--;
                skip.setText("跳过 " + left + "s");
                main.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        guide = this instanceof GuideActivity;
        ui.bars(guide ? Ui.BG : 0xFFFBE3B8);
        showAd();
    }

    private void showAd() {
        FrameLayout root = new FrameLayout(this);
        if (guide) {
            root.setBackgroundColor(Ui.BG);
        } else {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{0xFFFBE3B8, 0xFFF7C9D6});
            root.setBackground(g);
        }
        LinearLayout center = ui.frame(true);
        center.setGravity(Gravity.CENTER_HORIZONTAL);
        ui.pad(center, 28, 0, 28, 0);
        TextView badge = ui.badge(guide ? "引导页" : "广告", guide ? Ui.SOON : 0xCCFFFFFF, Ui.INK_2);
        center.addView(badge);
        TextView title = ui.bold(guide ? "欢迎体验新版功能" : "品牌开屏推广展示区", 26, Ui.INK);
        title.setGravity(Gravity.CENTER);
        ui.add(center, title, 14);
        TextView desc = ui.text(guide
            ? "这里的按钮也叫「跳过」，但页面没有广告标识。轻跳应当不点，等你自己关。"
            : "轻跳已开启、并在生效范围里打开了「内置测试页」时，右上角的按钮会被自动点掉。", 14, Ui.INK_2);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(0, 1.35f);
        ui.add(center, desc, 10);
        root.addView(center, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        skip = ui.medium(guide ? "跳过" : "跳过 5s", 13, Ui.INK);
        if (!guide) skip.setId(R.id.demo_skip);
        skip.setGravity(Gravity.CENTER);
        skip.setMinHeight(ui.dp(34));
        ui.pad(skip, 14, 4, 14, 4);
        skip.setBackground(ui.pressable(ui.shape(0xCCFFFFFF, 17), ui.shape(0xFFFFFFFF, 17)));
        skip.setClickable(true);
        skip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { result(); }
        });
        // 广告页：右上角小按钮；引导页：底部居中（引导页常见位置，也不吃角落加分）
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2,
            guide ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL : Gravity.TOP | Gravity.END);
        sp.topMargin = ui.dp(16);
        sp.rightMargin = ui.dp(16);
        sp.bottomMargin = ui.dp(guide ? 160 : 0);
        root.addView(skip, sp);
        setContentView(root);
        if (!guide) main.postDelayed(countdown, 1000);
    }

    private void result() {
        main.removeCallbacks(countdown);
        ui.bars(Ui.BG);
        LinearLayout page = ui.frame(true);
        page.setBackgroundColor(Ui.BG);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        ui.pad(page, 28, 96, 28, 28);
        page.addView(ui.badge("已跳过", Ui.TAG_BG, Ui.TAG_INK));
        TextView t = ui.bold("已进入示例首页", 26, Ui.INK);
        ui.add(page, t, 14);
        TextView d = ui.text(guide ? "你手动关掉了引导页。轻跳没有替你点，说明误触保护在工作。"
            : "如果你没动手，就是轻跳替你点掉了开屏广告。", 14, Ui.INK_2);
        d.setGravity(Gravity.CENTER);
        d.setLineSpacing(0, 1.35f);
        ui.add(page, d, 8);
        TextView back = ui.primary("返回", new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = ui.dp(28);
        page.addView(back, bp);
        setContentView(page);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(countdown);
        super.onDestroy();
    }
}
