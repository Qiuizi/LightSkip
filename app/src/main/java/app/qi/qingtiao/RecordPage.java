package app.qi.qingtiao;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 录制规则正式页：无悬浮窗、零新增权限。
 * 选应用 → SkipRecorder.arm() 后拉起目标应用 → 无障碍在前 5 秒采集候选按钮 →
 * 回到轻跳单选一个候选 → Scope.saveRecordedRule() 保存为本机规则。
 */
final class RecordPage extends PageActivity.Page implements SkipRecorder.Listener, AppCatalog.Listener {
    /** 进程被杀后，从 RecordingStore 读回多久以内的录制。 */
    private static final long RESTORE_WINDOW_MS = 30 * 60 * 1000L;
    private static final String DISMISSED = "record_dismissed_at";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final SkipRecorder recorder = SkipRecorder.get();

    private String target;
    private LinearLayout chips;
    private TextView startBtn;
    private TextView statusLine;
    private TextView coldHint;

    private LinearLayout resultBox;
    private TextView resultTitle;
    private LinearLayout candidates;
    private TextView saveBtn;
    private Recording shownRec;
    private int choice = -1;
    private final List<View> choiceRows = new ArrayList<View>();

    private LinearLayout savedGroup;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            bindState();
            SkipRecorder.State s = recorder.state(SystemClock.uptimeMillis());
            if (s == SkipRecorder.State.ARMED || s == SkipRecorder.State.CAPTURING) main.postDelayed(this, 500);
        }
    };

    RecordPage(PageActivity a) {
        super(a);
        target = a.getIntent().getStringExtra(PageActivity.EXTRA_PKG);
    }

    @Override String title() { return "录制规则"; }

    @Override
    View build() {
        LinearLayout body = scrollPage("不用悬浮窗，也不加新权限。打开目标应用等 5 秒，回来选一下跳过按钮。");

        // 第 1 步：选应用
        LinearLayout step1 = ui.frame(true);
        step1.setBackground(ui.shape(Ui.FIELD_SOFT, 20));
        ui.pad(step1, 16, 16, 16, 16);
        TextView badge = ui.badge("零悬浮窗 · 只记按钮信息", Ui.INK, Ui.FIELD_ON);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        step1.addView(badge, bp);
        ui.add(step1, ui.bold("1. 选一个还没跳掉的应用", 17, Ui.INK), 10);
        TextView d1 = ui.text("点下面开始后，轻跳会帮你打开它，并在前 5 秒记下开屏里出现过的按钮（不截屏）。", 13, Ui.INK_2);
        d1.setLineSpacing(0, 1.3f);
        ui.add(step1, d1, 4);
        HorizontalScrollView hs = new HorizontalScrollView(a);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chips = ui.frame(false);
        hs.addView(chips);
        ui.add(step1, hs, 12);
        ui.add(body, step1, 18, 16, 0);

        startBtn = ui.primary("先选一个应用", new View.OnClickListener() {
            @Override public void onClick(View v) { onStart(); }
        });
        ui.add(body, startBtn, 16, 16, 0);
        statusLine = ui.text("", 13, Ui.INK_2);
        statusLine.setGravity(Gravity.CENTER);
        ui.add(body, statusLine, 8, 16, 0);
        coldHint = ui.text("小提示：先在最近任务里把它划掉再录，冷启动才会出开屏广告。", 12, Ui.INK_3);
        coldHint.setGravity(Gravity.CENTER);
        coldHint.setLineSpacing(0, 1.25f);
        ui.add(body, coldHint, 2, 24, 0);

        // 第 2 步：选候选
        resultBox = ui.frame(true);
        resultTitle = ui.text("2. 回到轻跳，选哪个是跳过按钮", 12.5f, Ui.INK_2);
        ui.add(resultBox, resultTitle, 0, 8, 6);
        candidates = ui.group();
        resultBox.addView(candidates);
        LinearLayout btns = ui.frame(false);
        TextView again = ui.secondary("重新采集", new View.OnClickListener() {
            @Override public void onClick(View v) { onStart(); }
        });
        saveBtn = ui.primary("保存这条规则", new View.OnClickListener() {
            @Override public void onClick(View v) { onSave(); }
        });
        ui.fill(btns, again);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sp.leftMargin = ui.dp(10);
        btns.addView(saveBtn, sp);
        ui.add(resultBox, btns, 12);
        ui.add(body, resultBox, 22, 16, 0);
        resultBox.setVisibility(View.GONE);

        // 已保存的规则
        savedGroup = ui.addGroup(body, "已录制的规则");
        footnote(body, "录制只保存按钮文字、控件 ID 和相对屏幕位置，全部留在你手机里。导入的规则也会列在这里。", 14);
        return root(body);
    }

    // ---- 第 1 步 ----

    private void bindChips() {
        chips.removeAllViews();
        List<AppInfo> pick = suggestions();
        if (target != null && !containsPkg(pick, target)) {
            AppInfo t = catalog.find(target);
            if (t != null) pick.add(0, t);
        }
        for (final AppInfo info : pick) {
            boolean on = info.pkg.equals(target);
            TextView c = ui.medium(info.label, 13.5f, on ? Ui.WHITE : Ui.INK);
            c.setGravity(Gravity.CENTER);
            c.setMinHeight(ui.dp(36));
            ui.pad(c, 14, 6, 14, 6);
            c.setBackground(on ? ui.shape(Ui.INK, 18) : ui.outlined(Ui.CARD, Ui.LINE, 18));
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { select(info.pkg); }
            });
            ui.wrap(chips, c, chips.getChildCount() == 0 ? 0 : 8);
        }
        TextView more = ui.medium("其他应用…", 13.5f, Ui.INK_2);
        more.setGravity(Gravity.CENTER);
        more.setMinHeight(ui.dp(36));
        ui.pad(more, 14, 6, 14, 6);
        more.setBackground(ui.outlined(Ui.CARD, Ui.LINE, 18));
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showPicker(); }
        });
        ui.wrap(chips, more, chips.getChildCount() == 0 ? 0 : 8);
    }

    private static boolean containsPkg(List<AppInfo> l, String pkg) {
        for (AppInfo i : l) if (i.pkg.equals(pkg)) return true;
        return false;
    }

    /** 快捷芯片：优先已勾选却没有规则的，其次有规则但未验证的，最多 4 个。 */
    private List<AppInfo> suggestions() {
        List<AppInfo> out = new ArrayList<AppInfo>();
        if (!catalog.isLoaded()) return out;
        Set<String> picked = scope.selected();
        List<AppInfo> rest = new ArrayList<AppInfo>();
        for (AppInfo i : catalog.listable()) {
            if (i.isSelf()) continue;
            if (picked.contains(i.pkg) && !i.hasRule()) out.add(i);
            else if (picked.contains(i.pkg) && !i.verified()) rest.add(i);
        }
        out.addAll(rest);
        if (out.isEmpty()) {
            for (AppInfo i : catalog.listable()) if (!i.isSelf() && !i.hasRule()) out.add(i);
        }
        return out.size() > 4 ? new ArrayList<AppInfo>(out.subList(0, 4)) : out;
    }

    private void select(String pkg) {
        target = pkg;
        bindChips();
        bindState();
    }

    private void showPicker() {
        final List<AppInfo> apps = new ArrayList<AppInfo>();
        for (AppInfo i : catalog.listable()) if (!i.isSelf()) apps.add(i);
        LinearLayout box = ui.frame(true);
        ui.pad(box, 0, 18, 0, 10);
        TextView t = ui.bold("选一个要录制的应用", 18, Ui.INK);
        ui.add(box, t, 0, 22, 8);
        final Dialog[] d = new Dialog[1];
        ListView lv = new ListView(a);
        lv.setDivider(null);
        lv.setSelector(new ColorDrawable(0));
        lv.setVerticalScrollBarEnabled(false);
        final int px = ui.dp(AppIcon.SIZE_DP);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return apps.size(); }
            @Override public Object getItem(int p) { return apps.get(p); }
            @Override public long getItemId(int p) { return p; }
            @Override public View getView(int p, View cv, ViewGroup parent) {
                if (cv == null) {
                    LinearLayout row = ui.frame(false);
                    row.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(56)));
                    ui.pad(row, 22, 0, 22, 0);
                    row.setBackground(ui.pressableClear());
                    ImageView icon = AppIcon.create(ui);
                    ui.fixed(row, icon, AppIcon.SIZE_DP, AppIcon.SIZE_DP, 0);
                    LinearLayout col = ui.frame(true);
                    col.addView(ui.line(ui.medium("", 15, Ui.INK)));
                    col.addView(ui.text("", 11, Ui.INK_3));
                    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                    cp.leftMargin = ui.dp(14);
                    row.addView(col, cp);
                    cv = row;
                }
                LinearLayout row = (LinearLayout) cv;
                AppInfo info = apps.get(p);
                AppIcon.bind((ImageView) row.getChildAt(0), info.pkg, catalog, px, false);
                LinearLayout col = (LinearLayout) row.getChildAt(1);
                ((TextView) col.getChildAt(0)).setText(info.label);
                ((TextView) col.getChildAt(1)).setText(info.hasCustomRule() ? "已录制过" : info.ruleTag());
                return cv;
            }
        });
        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override public void onItemClick(android.widget.AdapterView<?> parent, View v, int p, long id) {
                d[0].dismiss();
                select(apps.get(p).pkg);
            }
        });
        int h = Math.min(ui.dp(56) * Math.max(apps.size(), 1), Math.round(a.getResources().getDisplayMetrics().heightPixels * 0.6f));
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        d[0] = ui.sheet(box);
        d[0].show();
    }

    private String label(String pkg) {
        AppInfo i = catalog.find(pkg);
        return i == null ? pkg : i.label;
    }

    private void onStart() {
        if (target == null) {
            showPicker();
            return;
        }
        SkipRecorder.State s = recorder.state(SystemClock.uptimeMillis());
        if (s == SkipRecorder.State.ARMED || s == SkipRecorder.State.CAPTURING) {
            recorder.cancel();
            toast("已取消录制");
            bindState();
            return;
        }
        if (!Device.accessibilityOn(a)) {
            ui.confirm("先开启无障碍服务", "录制要靠无障碍服务读取按钮信息。开好后回到这里再点一次。",
                "去开启", false, new Runnable() {
                    @Override public void run() { Device.openAccessibility(a); }
                }).show();
            return;
        }
        Intent launch = a.getPackageManager().getLaunchIntentForPackage(target);
        if (launch == null) {
            toast("打不开「" + label(target) + "」，它没有桌面入口");
            return;
        }
        if (!recorder.arm(target, SystemClock.uptimeMillis())) {
            toast("这个应用受保护，不能录制");
            return;
        }
        shownRec = null;
        choice = -1;
        resultBox.setVisibility(View.GONE);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(launch);
        } catch (Exception e) {
            recorder.cancel();
            toast("打不开「" + label(target) + "」");
        }
        main.removeCallbacks(tick);
        main.post(tick);
    }

    private void bindState() {
        long now = SystemClock.uptimeMillis();
        SkipRecorder.State s = recorder.state(now);
        boolean mine = target != null && target.equals(recorder.target());
        String name = target == null ? null : label(target);
        coldHint.setVisibility(View.VISIBLE);
        if (target == null) {
            startBtn.setText("先选一个应用");
            statusLine.setVisibility(View.GONE);
        } else if (mine && s == SkipRecorder.State.ARMED) {
            startBtn.setText("取消录制");
            statusLine.setText("等「" + name + "」打开… 还剩 " + secs(recorder.remainingMs(now)) + " 秒");
            statusLine.setVisibility(View.VISIBLE);
        } else if (mine && s == SkipRecorder.State.CAPTURING) {
            startBtn.setText("取消录制");
            statusLine.setText("正在采集「" + name + "」的按钮… " + secs(recorder.remainingMs(now)) + " 秒");
            statusLine.setVisibility(View.VISIBLE);
        } else {
            boolean done = shownRec != null && target.equals(shownRec.pkg);
            startBtn.setText((done ? "重新打开「" : "打开「") + name + "」" + (done ? "采集 5 秒" : "并开始 5 秒采集"));
            if (mine && s == SkipRecorder.State.TIMED_OUT) {
                statusLine.setText("等了 60 秒没看到「" + name + "」打开，再点一次试试。");
                statusLine.setVisibility(View.VISIBLE);
            } else {
                statusLine.setVisibility(View.GONE);
            }
        }
        if (s == SkipRecorder.State.DONE) showRecording(recorder.lastRecording());
    }

    private static long secs(long ms) { return (ms + 999) / 1000; }

    // ---- 第 2 步 ----

    private void showRecording(Recording rec) {
        if (rec == null || rec == shownRec) return;
        if (rec.createdAt == Device.prefs(a).getLong(DISMISSED, -1)) return;
        shownRec = rec;
        if (!rec.pkg.equals(target)) {
            target = rec.pkg;
            bindChips();
        }
        candidates.removeAllViews();
        choiceRows.clear();
        choice = -1;
        int n = Math.min(rec.candidates.size(), 8);
        resultTitle.setText("2. 回到轻跳，选哪个是「" + label(rec.pkg) + "」的跳过按钮");
        if (n == 0) {
            TextView none = ui.text("这 5 秒里没看到像按钮的东西。可能这次没出广告——先在最近任务里划掉它，再重新采集。",
                13.5f, Ui.INK_2);
            none.setLineSpacing(0, 1.3f);
            ui.pad(none, 16, 18, 16, 18);
            candidates.addView(none);
        }
        for (int i = 0; i < n; i++) {
            final int idx = i;
            RecordedNode node = rec.candidates.get(i);
            View row = candidateRow(node, i == 0);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { choose(idx); }
            });
            candidates.addView(row);
            choiceRows.add(row);
        }
        if (n > 0 && rec.candidates.get(0).hasId()) choose(0);
        else bindChoice();
        resultBox.setVisibility(View.VISIBLE);
        bindState();
    }

    private View candidateRow(RecordedNode node, boolean top) {
        LinearLayout row = ui.frame(false);
        row.setMinimumHeight(ui.dp(64));
        ui.pad(row, 16, 12, 16, 12);
        LinearLayout col = ui.frame(true);
        LinearLayout head = ui.frame(false);
        head.addView(ui.line(ui.bold(node.displayText(), 15, Ui.INK)));
        if (top) ui.wrap(head, ui.badge("推荐", Ui.INK, Ui.FIELD_ON), 8);
        col.addView(head);
        int[] c = node.centerPercent();
        String where = c[0] < 0 ? "位置未知" : place(c[0], c[1]) + "（" + c[0] + "%, " + c[1] + "%）";
        ui.add(col, ui.text(where, 12, Ui.INK_2), 2);
        TextView id = node.hasId() ? ui.mono(shortId(node.viewId), 11, Ui.INK_3)
            : ui.text("没有 ID · 暂时录不了", 11, Ui.WARN);
        ui.line(id);
        ui.add(col, id, 2);
        ui.fill(row, col);
        View radio = new View(a);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ui.dp(20), ui.dp(20));
        rp.leftMargin = ui.dp(12);
        row.addView(radio, rp);
        row.setTag(radio);
        return row;
    }

    private static String shortId(String id) {
        int slash = id.indexOf(":id/");
        return slash >= 0 ? id.substring(slash + 4) : id;
    }

    /** 屏幕百分比 → 口语位置。 */
    static String place(int x, int y) {
        String v = y < 25 ? "上" : y > 75 ? "下" : "中";
        String h = x < 34 ? "左" : x > 66 ? "右" : "中";
        if (v.equals("中") && h.equals("中")) return "屏幕中间";
        if (h.equals("中")) return v.equals("上") ? "顶部" : "底部";
        if (v.equals("中")) return h + "侧";
        return h + v + "角";
    }

    private void choose(int idx) {
        choice = idx;
        bindChoice();
    }

    private void bindChoice() {
        for (int i = 0; i < choiceRows.size(); i++) {
            View row = choiceRows.get(i);
            View radio = (View) row.getTag();
            boolean on = i == choice;
            row.setBackgroundColor(on ? Ui.FIELD_SOFT : Ui.CARD);
            android.graphics.drawable.GradientDrawable ring = ui.oval(Ui.CARD);
            ring.setStroke(ui.dp(on ? 2 : 1.5f), on ? Ui.INK : Ui.INK_3);
            if (on) {
                android.graphics.drawable.LayerDrawable l = new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{ring, ui.oval(Ui.INK)});
                l.setLayerInset(1, ui.dp(5), ui.dp(5), ui.dp(5), ui.dp(5));
                radio.setBackground(l);
            } else {
                radio.setBackground(ring);
            }
        }
        ui.enable(saveBtn, choice >= 0, true);
    }

    private void onSave() {
        final Recording rec = shownRec;
        if (rec == null) {
            toast("录制结果丢了，重新采集一次");
            return;
        }
        String name = label(rec.pkg);
        SkipRecorder.SaveCode code = scope.saveRecordedRule(rec, choice, name);
        switch (code) {
            case OK: {
                boolean addPick = !scope.all() && !scope.selected().contains(rec.pkg);
                if (addPick) scope.pick(rec.pkg, true);
                toast("已保存。下次打开「" + name + "」会自动跳过" + (addPick ? "，并已在生效范围里打开它" : ""));
                Device.prefs(a).edit().putLong(DISMISSED, rec.createdAt).apply();
                resultBox.setVisibility(View.GONE);
                shownRec = null;
                recorder.reset();
                bindSaved();
                bindState();
                break;
            }
            case NO_ID:
                toast("这个按钮没有 ID，暂时录不了。换一个，或重新采集");
                break;
            case BAD_INDEX:
                toast("先选一个按钮");
                break;
            case NO_RECORDING:
                toast("录制结果丢了，重新采集一次");
                break;
            case PROTECTED:
                toast("受保护的应用不能录规则");
                break;
            case BAD_EXISTING_JSON:
                toast("已导入的规则格式有误，先去「更多 → 导入规则」修好或清空");
                break;
            default:
                break;
        }
    }

    // ---- 已保存的规则 ----

    private void bindSaved() {
        savedGroup.removeAllViews();
        List<RuleBook.Entry> list = RuleBook.customEntries();
        if (list.isEmpty()) {
            LinearLayout empty = ui.frame(true);
            empty.setGravity(Gravity.CENTER);
            ui.pad(empty, 16, 22, 16, 22);
            TextView t = ui.bold("还没有录制过的规则", 15, Ui.INK);
            t.setGravity(Gravity.CENTER);
            empty.addView(t);
            TextView d = ui.text("选上面的应用点一下开始就能录。", 13, Ui.INK_2);
            d.setGravity(Gravity.CENTER);
            ui.add(empty, d, 4);
            savedGroup.addView(empty);
            return;
        }
        for (final RuleBook.Entry e : list) {
            StringBuilder ids = new StringBuilder();
            for (String id : e.ids) {
                if (ids.length() > 0) ids.append(" · ");
                ids.append(shortId(id));
            }
            FrameLayout trash = new FrameLayout(a);
            trash.setBackground(ui.pressable(ui.oval(0x00FFFFFF), ui.oval(Ui.INK_8)));
            ImageView g = ui.glyph(Glyph.TRASH, Ui.DANGER, 20);
            trash.addView(g, new FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER));
            trash.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
            trash.setContentDescription("删除这条规则");
            trash.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { confirmDelete(e); }
            });
            Ui.Item it = ui.item(Glyph.RECORD, label(e.pkg), ids.length() == 0 ? e.pkg : "按钮 ID：" + ids, trash, null);
            it.desc.setTypeface(android.graphics.Typeface.MONOSPACE);
            it.desc.setTextSize(11);
            savedGroup.addView(it.row);
        }
    }

    private void confirmDelete(final RuleBook.Entry e) {
        ui.confirm("删除「" + label(e.pkg) + "」的规则？", "删除后这个应用会回到" +
                (RuleBook.hasBuiltinRule(e.pkg) ? "内置专属规则。" : "智能识别。"),
            "删除", true, new Runnable() {
                @Override public void run() {
                    if (!scope.removeCustomRule(e.pkg)) {
                        toast("规则格式有误，去「导入规则」里清空");
                        return;
                    }
                    final String pkg = e.pkg;
                    exec(new Runnable() {
                        @Override public void run() { new RecordingStore(a.getFilesDir()).deleteAll(pkg); }
                    });
                    toast("已删除");
                    bindSaved();
                }
            }).show();
    }

    // ---- 生命周期 ----

    @Override
    void onResume() {
        recorder.addListener(this);
        catalog.addListener(this);
        bindChips();
        bindSaved();
        Recording last = recorder.lastRecording();
        if (last != null && (target == null || last.pkg.equals(target))) showRecording(last);
        else if (last == null) restoreFromDisk();
        bindState();
        main.removeCallbacks(tick);
        main.post(tick);
    }

    /** 进程被杀后内存里没有录制：后台读最近一份，30 分钟内的才拿回来点选。 */
    private void restoreFromDisk() {
        final String pkg = target;
        if (pkg == null) return;
        exec(new Runnable() {
            @Override public void run() {
                final Recording r = new RecordingStore(a.getFilesDir()).latest(pkg);
                if (r == null || System.currentTimeMillis() - r.createdAt > RESTORE_WINDOW_MS) return;
                main.post(new Runnable() {
                    @Override public void run() {
                        if (a.isFinishing() || recorder.lastRecording() != null) return;
                        recorder.restore(r);
                        showRecording(r);
                    }
                });
            }
        });
    }

    @Override
    void onPause() {
        recorder.removeListener(this);
        catalog.removeListener(this);
        main.removeCallbacks(tick);
    }

    @Override
    void onDestroy() {
        main.removeCallbacks(tick);
        io.shutdown();
        if (a.isFinishing()) {
            SkipRecorder.State s = recorder.state(SystemClock.uptimeMillis());
            if (s == SkipRecorder.State.ARMED || s == SkipRecorder.State.CAPTURING) recorder.cancel();
            else if (s != SkipRecorder.State.IDLE) recorder.reset();
        }
    }

    @Override
    public void onCatalogChanged() {
        bindChips();
        bindSaved();
        bindState();
    }

    @Override
    public void onRecorderChanged(SkipRecorder.State state) {
        main.post(new Runnable() {
            @Override public void run() {
                if (!a.isFinishing()) bindState();
            }
        });
    }

    private void exec(Runnable r) {
        try {
            io.execute(r);
        } catch (java.util.concurrent.RejectedExecutionException closed) {
            // 页面已关闭，丢弃。
        }
    }
}
