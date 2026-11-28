package app.qi.qingtiao;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 生效范围页（防卡顿核心）：整页就是一个 ListView。
 * 模式卡、推荐条、搜索框、计数行放在 addHeaderView；「永不点击」组和说明放在 addFooterView。
 * 行用 ViewHolder 复用，图标异步取 36dp 缓存位图；搜索 150ms 防抖；单行开关只改这一行和计数。
 */
final class ScopePage extends PageActivity.Page implements AppCatalog.Listener {
    private static final long DEBOUNCE_MS = 150;

    private final Handler main = new Handler(Looper.getMainLooper());
    private ListView list;
    private final Adapter adapter = new Adapter();
    private List<AppInfo> source = new ArrayList<AppInfo>();
    private List<AppInfo> rows = new ArrayList<AppInfo>();
    private String query = "";
    private int iconPx;

    /** 缓存的勾选集合（仅勾选模式）或排除集合（全部应用模式），避免每次 getView 读 SharedPreferences。 */
    private Set<String> picked = new HashSet<String>();
    private Set<String> excluded = new HashSet<String>();
    private boolean all;

    private Ui.ModeCard cardPicked;
    private Ui.ModeCard cardAll;
    private TextView warnBar;
    private LinearLayout recBar;
    private TextView recDesc;
    private TextView countText;
    private TextView emptyText;
    private LinearLayout lockedBox;
    private LinearLayout lockedGroup;

    private final Runnable applySearch = new Runnable() {
        @Override public void run() { filter(); }
    };

    ScopePage(PageActivity a) { super(a); }

    @Override String title() { return "生效范围"; }

    @Override
    View build() {
        a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        iconPx = ui.dp(AppIcon.SIZE_DP);
        list = new ListView(a);
        list.setBackgroundColor(Ui.BG);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new ColorDrawable(0));
        list.setCacheColorHint(0);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        list.setItemsCanFocus(true);
        list.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        list.addHeaderView(buildHeader(), null, false);
        list.addFooterView(buildFooter(), null, false);
        list.setAdapter(adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView v, int state) {}
            @Override public void onScroll(AbsListView v, int first, int visible, int total) {
                View top = v.getChildAt(0);
                boolean past = first > 0 || (top != null && top.getTop() < -ui.dp(60));
                a.nav.reveal(past);
            }
        });
        return list;
    }

    // ---- 头部 ----

    private View buildHeader() {
        LinearLayout h = ui.frame(true);
        h.addView(header(null));

        cardPicked = ui.modeCard("仅勾选的应用", "默认", null);
        cardPicked.card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setMode(false); }
        });
        ui.add(h, cardPicked.card, 18, 16, 0);
        cardAll = ui.modeCard("全部应用", null, null);
        cardAll.card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (all) return;
                ui.confirm("切换到「全部应用」？",
                    "轻跳会在所有应用里找「跳过」按钮（受保护的应用除外）",
                    "切换", false, new Runnable() {
                        @Override public void run() { setMode(true); }
                    }).show();
            }
        });
        ui.add(h, cardAll.card, 10, 16, 0);

        SpannableStringBuilder w = new SpannableStringBuilder();
        w.append(Ui.boldThen("当前对全部应用生效，", "建议 ", Ui.WARN));
        
        Ui.appendLink(w, "改回仅勾选", Ui.WARN, new Runnable() {
            @Override public void run() { setMode(false); }
        });
        warnBar = ui.text(w, 13.5f, Ui.WARN);
        warnBar.setLineSpacing(0, 1.25f);
        Ui.linkify(warnBar);
        warnBar.setBackground(ui.shape(Ui.WARN_BG, 14));
        ui.pad(warnBar, 14, 12, 14, 12);
        ui.add(h, warnBar, 12, 16, 0);

        recBar = ui.frame(false);
        recBar.setBackground(ui.shape(Ui.FIELD_SOFT, 18));
        ui.pad(recBar, 16, 14, 12, 14);
        LinearLayout rc = ui.frame(true);
        rc.addView(ui.bold("一键勾选推荐应用", 15, Ui.INK));
        recDesc = ui.text("", 12.5f, Ui.INK_2);
        recDesc.setLineSpacing(0, 1.2f);
        LinearLayout.LayoutParams rdp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rdp.topMargin = ui.dp(2);
        rc.addView(recDesc, rdp);
        ui.fill(recBar, rc);
        ui.wrap(recBar, ui.pill("一键勾选", Ui.INK, Ui.WHITE, new View.OnClickListener() {
            @Override public void onClick(View v) { pickRecommended(); }
        }), 10);
        ui.add(h, recBar, 12, 16, 0);

        final EditText search = ui.input("搜索应用名称", false);
        search.setSingleLine(true);
        Glyph g = new Glyph(Glyph.SEARCH, Ui.INK_3, ui.dp(1.7f));
        g.setBounds(0, 0, ui.dp(18), ui.dp(18));
        search.setCompoundDrawablesRelative(g, null, null, null);
        search.setCompoundDrawablePadding(ui.dp(10));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString();
                main.removeCallbacks(applySearch);
                main.postDelayed(applySearch, DEBOUNCE_MS);
            }
        });
        ui.add(h, search, 14, 16, 0);

        LinearLayout countRow = ui.frame(false);
        ui.pad(countRow, 24, 16, 16, 8);
        countText = ui.text("", 12.5f, Ui.INK_2);
        ui.fill(countRow, countText);
        TextView allOn = ui.bold("全部打开", 12.5f, Ui.INK);
        ui.pad(allOn, 8, 6, 8, 6);
        allOn.setBackground(ui.pressableColor(0x00FFFFFF, 10));
        allOn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setAll(true); }
        });
        countRow.addView(allOn);
        TextView allOff = ui.bold("全部关闭", 12.5f, Ui.INK);
        ui.pad(allOff, 8, 6, 8, 6);
        allOff.setBackground(ui.pressableColor(0x00FFFFFF, 10));
        allOff.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                ui.confirm("全部关闭？", "列表里的应用都会关掉，轻跳在这些应用里都不再点。之后可以一个个再打开。",
                    "全部关闭", true, new Runnable() {
                        @Override public void run() { setAll(false); }
                    }).show();
            }
        });
        ui.wrap(countRow, allOff, 4);
        h.addView(countRow);
        return h;
    }

    // ---- 尾部 ----

    private View buildFooter() {
        LinearLayout f = ui.frame(true);
        ui.pad(f, 0, 0, 0, 32);
        emptyText = ui.text("", 13.5f, Ui.INK_2);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setLineSpacing(0, 1.3f);
        emptyText.setBackground(ui.outlined(Ui.CARD, Ui.LINE, 18));
        ui.pad(emptyText, 20, 26, 20, 26);
        ui.add(f, emptyText, 0, 16, 0);

        lockedBox = ui.frame(true);
        lockedGroup = ui.addGroup(lockedBox, "永不点击 · 不能打开");
        f.addView(lockedBox);
        footnote(f, "列表里只显示已安装应用，系统应用、桌面和输入法不会出现", 14);
        return f;
    }

    private void bindLocked() {
        List<AppInfo> locked = catalog.locked();
        lockedGroup.removeAllViews(); // 只有几行（微信、QQ、支付宝…），不在滚动列表里
        lockedBox.setVisibility(locked.isEmpty() ? View.GONE : View.VISIBLE);
        for (AppInfo info : locked) {
            LinearLayout row = ui.frame(false);
            row.setMinimumHeight(ui.dp(60));
            ui.pad(row, 14, 10, 16, 10);
            ImageView icon = AppIcon.create(ui);
            ui.fixed(row, icon, AppIcon.SIZE_DP, AppIcon.SIZE_DP, 0);
            AppIcon.bind(icon, info.pkg, catalog, iconPx, true);
            LinearLayout col = ui.frame(true);
            col.addView(ui.line(ui.medium(info.label, 15, Ui.INK_3)));
            col.addView(ui.text("受保护 · 轻跳不会点击", 11.5f, Ui.INK_3));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cp.leftMargin = ui.dp(14);
            row.addView(col, cp);
            row.addView(ui.glyph(Glyph.LOCK, Ui.INK_3, 18));
            lockedGroup.addView(row);
        }
    }

    // ---- 数据 ----

    private void reloadState() {
        all = scope.all();
        picked = scope.selected();
        excluded = scope.excluded();
    }

    private void reloadSource() {
        source = catalog.isLoaded() ? catalog.listable() : new ArrayList<AppInfo>();
        filter();
        bindLocked();
    }

    /** 过滤只在内存列表上做（与 catalog.search 同一套匹配规则：中文名或包名，忽略大小写）。 */
    private void filter() {
        rows = AppInfo.filter(source, query);
        adapter.notifyDataSetChanged();
        bindEmpty();
    }

    private void bindEmpty() {
        if (!catalog.isLoaded()) {
            emptyText.setText("正在读取已安装的应用…");
            emptyText.setVisibility(View.VISIBLE);
        } else if (rows.isEmpty()) {
            String q = query.trim();
            emptyText.setText(q.isEmpty() ? "还没找到可开启的应用"
                : "没找到「" + q + "」\n仅支持搜索已安装应用");
            emptyText.setVisibility(View.VISIBLE);
        } else {
            emptyText.setVisibility(View.GONE);
        }
    }

    private boolean isOn(String pkg) {
        return all ? !excluded.contains(pkg) : picked.contains(pkg);
    }

    private void bindTop() {
        cardPicked.set(!all);
        cardAll.set(all);
        warnBar.setVisibility(all ? View.VISIBLE : View.GONE);
        List<String> rec = ScopePolicy.recommended(catalog.installedPackages());
        int missing = 0;
        for (String p : rec) if (!picked.contains(p)) missing++;
        recBar.setVisibility(!all && catalog.isLoaded() && !rec.isEmpty() ? View.VISIBLE : View.GONE);
        recDesc.setText(missing == 0
            ? "本机 " + rec.size() + " 个有专属规则的应用都已勾选"
            : "本机装了 " + rec.size() + " 个有专属规则的常用应用，还差 " + missing + " 个没勾选");
        bindCount();
    }

    private void bindCount() {
        int on = 0, total = 0;
        for (AppInfo info : source) {
            if (info.isSelf()) continue;
            total++;
            if (isOn(info.pkg)) on++;
        }
        countText.setText(catalog.isLoaded() ? "已打开 " + on + " / " + total : "已打开 –");
    }

    private void setMode(boolean toAll) {
        if (toAll == all) return;
        scope.mode(toAll);
        reloadState();
        bindTop();
        adapter.notifyDataSetChanged(); // 开关含义变了：只重绑可见行
        toast(toAll ? "已切换到「全部应用」" : "已改回「仅勾选的应用」");
    }

    private void pickRecommended() {
        int n = scope.pickRecommended(catalog.installedPackages());
        reloadState();
        bindTop();
        adapter.notifyDataSetChanged();
        toast(n > 0 ? "已新增勾选 " + n + " 个推荐应用" : "推荐的应用都已经勾选了");
    }

    private void setAll(boolean on) {
        if (all) {
            for (AppInfo info : source) {
                if (on == excluded.contains(info.pkg)) scope.exclude(info.pkg, !on);
            }
        } else {
            Set<String> next = new HashSet<String>();
            if (on) for (AppInfo info : source) next.add(info.pkg);
            else if (picked.contains(RuleBook.SELF)) next.add(RuleBook.SELF); // 测试页保持原样
            scope.pickAll(next);
        }
        reloadState();
        bindTop();
        adapter.notifyDataSetChanged();
    }

    private void toggle(AppInfo info, Holder h) {
        boolean next = !isOn(info.pkg);
        if (all) {
            scope.exclude(info.pkg, !next);
            if (next) excluded.remove(info.pkg); else excluded.add(info.pkg);
        } else {
            if (!scope.pick(info.pkg, next)) {
                toast("这个应用受保护，不能打开");
                return;
            }
            if (next) picked.add(info.pkg); else picked.remove(info.pkg);
        }
        h.toggle.setChecked(next, true);
        bindCount();
        if (!all && recBar.getVisibility() == View.VISIBLE && info.hasRule()) bindTop();
    }

    // ---- 详情 ----

    private void showDetails(final AppInfo info) {
        LinearLayout box = ui.frame(true);
        ui.pad(box, 22, 22, 22, 18);
        LinearLayout head = ui.frame(false);
        ImageView icon = AppIcon.create(ui);
        ui.fixed(head, icon, 44, 44, 0);
        AppIcon.bind(icon, info.pkg, catalog, ui.dp(44), false);
        LinearLayout hc = ui.frame(true);
        hc.addView(ui.line(ui.bold(info.label, 18, Ui.INK)));
        TextView state = ui.text(isOn(info.pkg) ? "已打开 · 轻跳会在这里找「跳过」" : "未打开 · 轻跳不读取这个应用", 12.5f, Ui.INK_2);
        hc.addView(state);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hp.leftMargin = ui.dp(14);
        head.addView(hc, hp);
        box.addView(head);

        String how;
        if (info.hasCustomRule()) how = "你录制或导入的规则：按按钮 ID 点。";
        else if (info.hasBuiltinRule()) how = "内置专属规则：按按钮 ID 点" + (info.verified() ? "，已在真机验证。" : "。");
        else how = "智能识别：要同时看到「跳过」字样和广告标识，分数够高才点。";
        ui.add(box, detailRow("识别方式", how, false), 18);
        ui.add(box, detailRow("包名", info.pkg, true), 12);

        LinearLayout btns = ui.frame(false);
        final Dialog[] d = new Dialog[1];
        TextView rec = ui.secondary("去录制规则", new View.OnClickListener() {
            @Override public void onClick(View v) {
                d[0].dismiss();
                a.startActivity(new Intent(a, PageActivity.class)
                    .putExtra(PageActivity.EXTRA_PAGE, PageActivity.RECORD)
                    .putExtra(PageActivity.EXTRA_PKG, info.pkg));
            }
        });
        TextView ok = ui.primary("好的", new View.OnClickListener() {
            @Override public void onClick(View v) { d[0].dismiss(); }
        });
        ui.fill(btns, rec);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        op.leftMargin = ui.dp(10);
        btns.addView(ok, op);
        ui.add(box, btns, 22);
        d[0] = ui.sheet(box);
        d[0].show();
    }

    private View detailRow(String k, String v, boolean mono) {
        LinearLayout row = ui.frame(false);
        row.setGravity(Gravity.TOP);
        TextView kt = ui.bold(k, 13, Ui.INK);
        row.addView(kt, new LinearLayout.LayoutParams(ui.dp(72), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView vt = mono ? ui.mono(v, 12, Ui.INK_2) : ui.text(v, 13.5f, Ui.INK_2);
        vt.setLineSpacing(0, 1.25f);
        vt.setTextIsSelectable(mono);
        row.addView(vt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    // ---- 生命周期 ----

    @Override
    void onResume() {
        catalog.addListener(this);
        reloadState();
        reloadSource(); // 只读内存快照 + 重绑可见行；装卸包由 AppCatalog 广播增量刷新
        bindTop();
    }

    @Override
    void onPause() {
        catalog.removeListener(this);
    }

    @Override
    void onDestroy() {
        main.removeCallbacks(applySearch);
    }

    @Override
    public void onCatalogChanged() {
        reloadSource();
        bindTop();
    }

    // ---- 适配器 ----

    private static final class Holder {
        FrameLayout root;
        CardSlice bg;
        LinearLayout info;
        ImageView icon;
        TextView label;
        TextView tag;
        Toggle toggle;
        android.graphics.drawable.Drawable tagBg;
        AppInfo bound;
    }

    private final class Adapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public AppInfo getItem(int p) { return rows.get(p); }
        @Override public long getItemId(int p) { return p; }
        @Override public boolean hasStableIds() { return false; }
        @Override public boolean isEnabled(int p) { return false; } // 点击由行内两块区域各自处理

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            final Holder h;
            if (convert == null) {
                h = createRow();
                convert = h.root;
            } else {
                h = (Holder) convert.getTag();
            }
            AppInfo info = rows.get(position);
            h.bound = info;
            h.bg.setPosition(CardSlice.positionOf(position, rows.size()));
            h.label.setText(info.label);
            boolean rule = info.hasRule();
            h.tag.setText(info.isSelf() ? "测试页" : info.ruleTag());
            h.tag.setTextColor(rule ? Ui.TAG_INK : Ui.INK_3);
            h.tag.setBackground(rule ? h.tagBg : null);
            h.tag.setPadding(rule ? ui.dp(6) : 0, ui.dp(1), rule ? ui.dp(6) : 0, ui.dp(1));
            h.toggle.setChecked(isOn(info.pkg), false);
            AppIcon.bind(h.icon, info.pkg, catalog, iconPx, false);
            return convert;
        }

        private Holder createRow() {
            final Holder h = new Holder();
            h.tagBg = ui.shape(Ui.TAG_BG, 6); // 每行一份：Drawable 不能跨 View 共享 bounds
            h.root = new FrameLayout(a);
            h.root.setPadding(ui.dp(16), 0, ui.dp(16), 0);
            h.root.setLayoutParams(new AbsListView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout row = ui.frame(false);
            h.bg = new CardSlice(ui.dp(18), ui.dp(1), ui.dp(64));
            row.setBackground(h.bg);
            row.setMinimumHeight(ui.dp(62));
            ui.pad(row, 4, 0, 6, 0);
            h.root.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            h.info = ui.frame(false);
            h.info.setMinimumHeight(ui.dp(54));
            ui.pad(h.info, 10, 8, 8, 8);
            h.info.setBackground(ui.pressable(ui.shape(0x00FFFFFF, 14), ui.shape(Ui.INK_8, 14)));
            h.icon = AppIcon.create(ui);
            ui.fixed(h.info, h.icon, AppIcon.SIZE_DP, AppIcon.SIZE_DP, 0);
            LinearLayout col = ui.frame(true);
            h.label = ui.line(ui.bold("", 16.5f, Ui.INK));
            col.addView(h.label);
            h.tag = ui.text("", 12, Ui.INK_3);
            h.tag.setTypeface(Ui.MEDIUM);
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.topMargin = ui.dp(3);
            col.addView(h.tag, tp);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cp.leftMargin = ui.dp(14);
            h.info.addView(col, cp);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            ip.topMargin = ui.dp(4);
            ip.bottomMargin = ui.dp(4);
            row.addView(h.info, ip);

            h.toggle = new Toggle(a);
            row.addView(h.toggle);

            h.info.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (h.bound != null) showDetails(h.bound); }
            });
            h.toggle.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (h.bound != null) toggle(h.bound, h); }
            });
            h.root.setTag(h);
            return h;
        }
    }
}
