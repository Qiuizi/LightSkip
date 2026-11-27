package app.qi.qingtiao;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 最近跳过记录：后台线程读 HitLog（磁盘读），回主线程用 ListView 复用渲染；可复制导出、清空。 */
final class HitsPage extends PageActivity.Page {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Row> rows = new ArrayList<Row>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA);
    private BaseAdapter adapter;
    private TextView empty;
    private TextView summary;
    private HitLog log;

    private static final class Row {
        String pkg, text, id, result;
        int score;
        long ts;
    }

    HitsPage(PageActivity a) { super(a); }

    @Override String title() { return "最近跳过记录"; }

    @Override
    View build() {
        log = new HitLog(a.getFilesDir());
        ListView list = new ListView(a);
        list.setBackgroundColor(Ui.BG);
        list.setDivider(null);
        list.setSelector(new ColorDrawable(0));
        list.setVerticalScrollBarEnabled(false);

        LinearLayout head = ui.frame(true);
        head.addView(header("只留在你手机里，最多保存最近 500 条。用来确认点对了没有、有没有差点误点。"));
        LinearLayout btns = ui.frame(false);
        TextView copy = ui.secondary("复制全部", new View.OnClickListener() {
            @Override public void onClick(View v) { copyAll(); }
        });
        TextView clear = ui.secondary("清空", new View.OnClickListener() {
            @Override public void onClick(View v) {
                ui.confirm("清空跳过记录？", "只清这份明细，今日和累计次数不变。", "清空", true, new Runnable() {
                    @Override public void run() {
                        exec(new Runnable() {
                            @Override public void run() {
                                log.clear();
                                main.post(new Runnable() { @Override public void run() { load(); } });
                            }
                        });
                    }
                }).show();
            }
        });
        clear.setTextColor(Ui.DANGER);
        ui.fill(btns, copy);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = ui.dp(10);
        btns.addView(clear, cp);
        ui.add(head, btns, 16, 16, 0);
        summary = ui.text("", 12.5f, Ui.INK_2);
        ui.add(head, summary, 18, 24, 6);
        list.addHeaderView(head, null, false);

        LinearLayout foot = ui.frame(true);
        ui.pad(foot, 0, 0, 0, 32);
        empty = ui.text("正在读取…", 13.5f, Ui.INK_2);
        empty.setGravity(Gravity.CENTER);
        empty.setBackground(ui.outlined(Ui.CARD, Ui.LINE, 18));
        ui.pad(empty, 20, 26, 20, 26);
        ui.add(foot, empty, 0, 16, 0);
        footnote(foot, "「险些点击」= 看着像跳过按钮但分数不够，轻跳没有点。这些记录能帮你判断要不要录一条规则。", 12);
        list.addFooterView(foot, null, false);

        adapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int p) { return rows.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public boolean isEnabled(int p) { return false; }
            @Override public View getView(int p, View cv, ViewGroup parent) {
                Holder h;
                if (cv == null) {
                    h = new Holder();
                    FrameLayout root = new FrameLayout(a);
                    root.setLayoutParams(new AbsListView.LayoutParams(-1, -2));
                    root.setPadding(ui.dp(16), 0, ui.dp(16), 0);
                    LinearLayout row = ui.frame(false);
                    h.bg = new CardSlice(ui.dp(18), ui.dp(1), ui.dp(16));
                    row.setBackground(h.bg);
                    ui.pad(row, 16, 12, 14, 12);
                    LinearLayout col = ui.frame(true);
                    h.title = ui.line(ui.medium("", 15, Ui.INK));
                    col.addView(h.title);
                    h.sub = ui.line(ui.text("", 12, Ui.INK_2));
                    ui.add(col, h.sub, 2);
                    h.id = ui.line(ui.mono("", 11, Ui.INK_3));
                    ui.add(col, h.id, 2);
                    ui.fill(row, col);
                    h.tag = ui.badge("", Ui.SOON, Ui.INK_2);
                    ui.wrap(row, h.tag, 10);
                    root.addView(row, new FrameLayout.LayoutParams(-1, -2));
                    root.setTag(h);
                    cv = root;
                } else {
                    h = (Holder) cv.getTag();
                }
                Row r = rows.get(p);
                h.bg.setPosition(CardSlice.positionOf(p, rows.size()));
                AppInfo info = catalog.find(r.pkg);
                h.title.setText(info == null ? r.pkg : info.label);
                String what = r.text.isEmpty() ? "无文字按钮" : "「" + r.text + "」";
                h.sub.setText(what + " · " + r.score + " 分 · " + fmt.format(new Date(r.ts)));
                h.id.setText(r.id);
                h.id.setVisibility(r.id.isEmpty() ? View.GONE : View.VISIBLE);
                if ("clicked".equals(r.result)) {
                    h.tag.setText("已跳过");
                    h.tag.setBackground(ui.shape(Ui.TAG_BG, 7));
                    h.tag.setTextColor(Ui.TAG_INK);
                } else if ("near_miss".equals(r.result)) {
                    h.tag.setText("险些点击");
                    h.tag.setBackground(ui.shape(Ui.WARN_BG, 7));
                    h.tag.setTextColor(Ui.WARN);
                } else {
                    h.tag.setText("没点成");
                    h.tag.setBackground(ui.shape(Ui.SOON, 7));
                    h.tag.setTextColor(Ui.INK_2);
                }
                return cv;
            }
        };
        list.setAdapter(adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView v, int s) {}
            @Override public void onScroll(AbsListView v, int first, int vis, int total) {
                View top = v.getChildAt(0);
                a.nav.reveal(first > 0 || (top != null && top.getTop() < -ui.dp(60)));
            }
        });
        return list;
    }

    private static final class Holder {
        CardSlice bg;
        TextView title, sub, id, tag;
    }

    private void load() {
        exec(new Runnable() {
            @Override public void run() {
                List<String> lines = log.recent(100);
                final List<Row> out = new ArrayList<Row>();
                for (String line : lines) {
                    Row r = parse(line);
                    if (r != null) out.add(r);
                }
                Collections.reverse(out);
                main.post(new Runnable() {
                    @Override public void run() {
                        if (a.isFinishing()) return;
                        rows.clear();
                        rows.addAll(out);
                        adapter.notifyDataSetChanged();
                        int clicked = 0;
                        for (Row r : rows) if ("clicked".equals(r.result)) clicked++;
                        summary.setText(rows.isEmpty() ? "" : "最近 " + rows.size() + " 条 · 其中跳过 " + clicked + " 次");
                        empty.setText("还没有记录。打开一个勾选过的应用，看到开屏广告被跳过后这里就会有。");
                        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    /** 只展示点击、点击失败、险些点击三类；会话打开和无候选只在导出里有。 */
    private static Row parse(String line) {
        try {
            Object o = Json.parse(line);
            if (!(o instanceof Map)) return null;
            Map<?, ?> m = (Map<?, ?>) o;
            String result = str(m.get("result"));
            if (!"clicked".equals(result) && !"failed".equals(result) && !"near_miss".equals(result)) return null;
            Row r = new Row();
            r.result = result;
            r.pkg = str(m.get("pkg"));
            r.text = str(m.get("nodeText"));
            r.id = str(m.get("nodeId"));
            r.score = m.get("score") instanceof Number ? ((Number) m.get("score")).intValue() : 0;
            r.ts = m.get("ts") instanceof Number ? ((Number) m.get("ts")).longValue() : 0;
            return r;
        } catch (RuntimeException bad) {
            return null;
        }
    }

    private static String str(Object o) { return o instanceof String ? (String) o : ""; }

    private void copyAll() {
        exec(new Runnable() {
            @Override public void run() {
                final String text = log.exportText();
                main.post(new Runnable() {
                    @Override public void run() {
                        if (text.isEmpty()) {
                            toast("还没有可复制的记录");
                            return;
                        }
                        ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("轻跳跳过记录", text));
                        int n = text.split("\n").length;
                        toast("已复制 " + n + " 行记录（JSON Lines），可以直接发给开发者");
                    }
                });
            }
        });
    }

    @Override void onResume() { load(); }

    @Override void onDestroy() {
        io.shutdown();
        if (log != null) log.close();
    }

    private void exec(Runnable r) {
        try {
            io.execute(r);
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            // 页面已关闭，丢弃。
        }
    }
}
