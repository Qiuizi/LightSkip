package app.qi.qingtiao;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.Set;

/**
 * V5 首页：上半屏动态色场（缺权限 / 只缺保活 / 全就绪 / 已暂停）+ 信任说明 + 可选更新提示条 + 三组入口。
 * onResume 只读内存里的 Scope 和 AppCatalog 快照，不扫包、不重建视图。
 */
public final class MainActivity extends Activity implements AppCatalog.Listener {
    private enum State { MISSING, PARTIAL, READY, PAUSED }

    private Ui ui;
    private Scope scope;
    private AppCatalog catalog;

    private ScrollView scroll;
    private LinearLayout field;
    private GradientDrawable fieldBg;
    private int fieldColor = Ui.FIELD_ON;
    private ValueAnimator fieldAnim;
    private State shown;

    private TextView statusTitle;
    private TextView statusSub;
    private LinearLayout tasks;
    private TaskBar taskAcc;
    private TaskBar taskBat;
    private TextView restrictedNote;
    private RockerView rocker;

    private Ui.TrustRow trustScope;
    private LinearLayout updateBanner;
    private TextView updateTitle;
    private TextView updateSub;
    private TextView updateAction;
    private UpdateInfo availableUpdate;
    private AppUpdater.DownloadHandle downloadHandle;
    private File pendingInstallApk;
    private boolean silentCheckStarted;
    private boolean updateDownloading;
    private int updatePercent;
    private String updateErrorMsg;
    private String updateFallbackUrl = UpdateInfo.DEFAULT_RELEASE_PAGE;

    private Ui.Item scopeItem;
    private Ui.Item recordItem;
    private Ui.Item permItem;
    private Ui.Item aboutItem;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        scope = new Scope(this);
        catalog = AppCatalog.get(this);
        catalog.ensureLoaded();
        ui.bars(fieldColor);

        scroll = ui.scroller();
        LinearLayout page = ui.frame(true);
        ui.pad(page, 0, 0, 0, 28);
        scroll.addView(page);
        setContentView(scroll);
        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int x, int y, int ox, int oy) { syncStatusBar(); }
        });

        buildField(page);
        Ui.insetTop(field);
        Ui.insetBottom(page);
        buildTrust(page);
        buildUpdateBanner(page);
        buildGroups(page);

        TextView foot = ui.text("轻跳 v" + Ui.version(this), 12, Ui.INK_3);
        foot.setGravity(Gravity.CENTER);
        ui.add(page, foot, 24);
    }

    // ---- 色场 ----

    private void buildField(LinearLayout page) {
        field = ui.frame(true);
        fieldBg = ui.fieldBg(fieldColor);
        field.setBackground(fieldBg);
        ui.pad(field, 20, 10, 20, 22);
        page.addView(field);

        LinearLayout head = ui.frame(false);
        ui.pad(head, 0, 6, 0, 0);
        ui.fill(head, ui.bold("轻跳", 16, Ui.INK));
        head.addView(ui.text("v" + Ui.version(this), 12, Ui.INK_72));
        field.addView(head);

        statusTitle = ui.bold("正在替你跳", 44, Ui.INK);
        statusTitle.setLineSpacing(0, 1.12f);
        statusTitle.setLetterSpacing(-0.02f);
        ui.add(field, statusTitle, 26);
        statusSub = ui.text("", 15, Ui.INK_72);
        statusSub.setLineSpacing(0, 1.3f);
        ui.add(field, statusSub, 8);

        tasks = ui.frame(true);
        ui.add(field, tasks, 18);
        taskAcc = new TaskBar("1", "无障碍服务", "找到并点击「跳过」", "去开启");
        taskAcc.root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // 用户点「去开启」就是想让它跑起来：先把总开关拨到开，授权回来直接是「正在替你跳」。
                scope.enable(true);
                Device.openAccessibility(MainActivity.this);
            }
        });
        ui.add(tasks, taskAcc.root, 0);
        taskBat = new TaskBar("2", "后台保活", "忽略电池优化，防止被清理", "去配置");
        taskBat.root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Device.page(MainActivity.this, PageActivity.PERM); }
        });
        ui.add(tasks, taskBat.root, 10);

        SpannableStringBuilder note = new SpannableStringBuilder("侧载安装找不到开关？先 ");
        Ui.appendLink(note, "允许受限设置", Ui.INK, new Runnable() {
            @Override public void run() { Device.openAppDetails(MainActivity.this); }
        });
        note.append("，再回来开启");
        restrictedNote = ui.text(note, 13, Ui.INK_72);
        Ui.linkify(restrictedNote);
        ui.add(tasks, restrictedNote, 12, 4, 0);

        rocker = new RockerView(this);
        rocker.setOnToggle(new RockerView.OnToggle() {
            @Override public void onToggle(boolean on) {
                scope.enable(on);
                refresh(true);
            }
        });
        ui.add(field, rocker, 20);
    }

    /** 黑色任务条：编号圈 + 标题列 + 动作词；完成后变 8% 墨色浅条。 */
    private final class TaskBar {
        final LinearLayout root;
        final TextView idx;
        final ImageView alert;
        final TextView title;
        final TextView desc;
        final TextView action;
        final String actionText;
        final String number;

        TaskBar(String number, String t, String d, String act) {
            actionText = act;
            this.number = number;
            root = ui.frame(false);
            ui.pad(root, 16, 14, 18, 14);
            FrameLayout badge = new FrameLayout(MainActivity.this);
            idx = ui.bold(number, 13, Ui.FIELD_MISS);
            idx.setGravity(Gravity.CENTER);
            badge.addView(idx, new FrameLayout.LayoutParams(ui.dp(28), ui.dp(28), Gravity.CENTER));
            alert = new ImageView(MainActivity.this);
            alert.setImageDrawable(new Glyph(Glyph.ALERT, Ui.FIELD_ON, ui.dp(1.8f)));
            alert.setVisibility(View.GONE);
            badge.addView(alert, new FrameLayout.LayoutParams(ui.dp(28), ui.dp(28), Gravity.CENTER));
            ui.fixed(root, badge, 28, 28, 0);
            LinearLayout col = ui.frame(true);
            title = ui.bold(t, 16, Ui.WHITE);
            desc = ui.text(d, 12.5f, 0xB8FFFFFF);
            col.addView(title);
            LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dp2.topMargin = ui.dp(2);
            col.addView(desc, dp2);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cp.leftMargin = ui.dp(14);
            root.addView(col, cp);
            action = ui.bold(act, 14, Ui.FIELD_MISS);
            ui.wrap(root, action, 10);
        }

        /** accent：缺权限时用黄色，绿色色场上用薄荷绿。 */
        void set(boolean done, boolean alertStyle, int accent, String doneText) {
            root.setMinimumHeight(ui.dp(done ? 52 : 64));
            if (done) {
                root.setBackground(ui.pressableColor(Ui.INK_8, 20));
                GradientDrawable ring = ui.oval(Ui.INK);
                idx.setBackground(ring);
                idx.setText("✓");
                idx.setTextColor(Ui.WHITE);
                idx.setVisibility(View.VISIBLE);
                alert.setVisibility(View.GONE);
                title.setTextColor(Ui.INK);
                desc.setVisibility(View.GONE);
                action.setText(doneText);
                action.setTextColor(Ui.INK_72);
            } else {
                root.setBackground(ui.pressable(ui.shape(Ui.INK, 20), ui.shape(0xFF262C28, 20)));
                GradientDrawable ring = ui.oval(0);
                ring.setStroke(ui.dp(1.5f), accent);
                idx.setBackground(ring);
                idx.setText(alertStyle ? "" : number);
                idx.setTextColor(accent);
                idx.setVisibility(alertStyle ? View.INVISIBLE : View.VISIBLE);
                alert.setVisibility(alertStyle ? View.VISIBLE : View.GONE);
                title.setTextColor(Ui.WHITE);
                desc.setVisibility(View.VISIBLE);
                action.setText(actionText);
                action.setTextColor(accent);
            }
        }
    }

    // ---- 信任行 ----

    private void buildTrust(LinearLayout page) {
        LinearLayout box = ui.frame(true);
        ui.add(page, box, 12, 12, 0);
        trustScope = ui.trustRow(Glyph.SHIELD);
        box.addView(trustScope.row);
    }

    private void bindTrustScope(boolean all, int picked) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        if (all) {
            b.append(Ui.boldThen("当前为全部应用，", "建议 ", Ui.WARN));
            Ui.appendLink(b, "改回仅勾选", Ui.WARN, new Runnable() {
                @Override public void run() {
                    scope.mode(false);
                    Toast.makeText(MainActivity.this, "已改回「仅勾选的应用」", Toast.LENGTH_SHORT).show();
                    refresh(true);
                }
            });
            trustScope.setWarn(true);
            ((Glyph) trustScope.icon.getDrawable()).setColor(Ui.WARN);
        } else if (picked == 0) {
            b.append(Ui.boldThen("还没勾选应用，", "", Ui.WARN));
            Ui.appendLink(b, "去勾选", Ui.WARN, new Runnable() {
                @Override public void run() { Device.page(MainActivity.this, PageActivity.SCOPE); }
            });
            trustScope.setWarn(true);
            ((Glyph) trustScope.icon.getDrawable()).setColor(Ui.WARN);
        } else {
            b.append(Ui.boldThen("仅更新联网 · 仅勾选 " + picked + " 个", " · 支付银行不点", Ui.INK_2));
            trustScope.setWarn(false);
            ((Glyph) trustScope.icon.getDrawable()).setColor(Ui.INK);
        }
        trustScope.text.setText(b);
    }

    // ---- 轻量更新提示条（仅发现新版本时显示） ----

    private void buildUpdateBanner(LinearLayout page) {
        updateBanner = ui.frame(false);
        updateBanner.setBackground(ui.outlined(Ui.TAG_BG, 0xFFB8E6CF, 18));
        ui.pad(updateBanner, 14, 12, 12, 12);
        updateBanner.setVisibility(View.GONE);

        FrameLayout iconBox = new FrameLayout(this);
        iconBox.setBackground(ui.oval(Ui.CARD));
        ImageView icon = new ImageView(this);
        icon.setImageDrawable(new Glyph(Glyph.DOWNLOAD, Ui.TAG_INK, ui.dp(1.8f)));
        iconBox.addView(icon, new FrameLayout.LayoutParams(ui.dp(16), ui.dp(16), Gravity.CENTER));
        ui.fixed(updateBanner, iconBox, 30, 30, 0);

        LinearLayout col = ui.frame(true);
        updateTitle = ui.bold("", 14.5f, Ui.INK);
        updateSub = ui.text("", 12, Ui.TAG_INK);
        updateSub.setLineSpacing(0, 1.2f);
        col.addView(updateTitle);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = ui.dp(2);
        col.addView(updateSub, sp);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cp.leftMargin = ui.dp(10);
        updateBanner.addView(col, cp);

        updateAction = ui.pill("一键升级", Ui.INK, Ui.WHITE, new View.OnClickListener() {
            @Override public void onClick(View v) { onUpdateBannerAction(); }
        });
        ui.wrap(updateBanner, updateAction, 8);
        updateBanner.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onUpdateBannerAction(); }
        });

        ui.add(page, updateBanner, 8, 16, 0);
    }

    private void onUpdateBannerAction() {
        if (updateDownloading) {
            if (downloadHandle != null) downloadHandle.cancel();
            return;
        }
        if (pendingInstallApk != null) {
            if (AppUpdater.canInstallPackages(this)) {
                File f = pendingInstallApk;
                pendingInstallApk = null;
                AppUpdater.installApk(this, f);
                renderUpdateBanner();
            } else {
                promptInstallPermission();
            }
            return;
        }
        if (updateErrorMsg != null && !updateErrorMsg.isEmpty()) {
            ui.confirm(
                "升级未完成",
                updateErrorMsg + "\n\n你可以改用浏览器打开 GitHub Releases 手动下载。",
                "浏览器打开",
                false,
                new Runnable() {
                    @Override public void run() { AppUpdater.openReleasePage(MainActivity.this, updateFallbackUrl); }
                }
            ).show();
            return;
        }
        if (availableUpdate != null) {
            startBannerDownload(availableUpdate);
        }
    }

    private void startBannerDownload(final UpdateInfo info) {
        updateDownloading = true;
        updatePercent = 0;
        updateErrorMsg = null;
        updateFallbackUrl = info.releasePageUrl;
        renderUpdateBanner();
        downloadHandle = AppUpdater.downloadAndInstall(this, info, new AppUpdater.DownloadCallback() {
            @Override
            public void onProgress(long bytesRead, long totalBytes, int percent) {
                if (isFinishing() || isDestroyed()) return;
                updatePercent = Math.max(0, Math.min(100, percent));
                renderUpdateBanner();
            }

            @Override
            public void onNeedInstallPermission(File apkFile) {
                if (isFinishing() || isDestroyed()) return;
                downloadHandle = null;
                updateDownloading = false;
                pendingInstallApk = apkFile;
                renderUpdateBanner();
                promptInstallPermission();
            }

            @Override
            public void onReadyToInstall(File apkFile) {
                if (isFinishing() || isDestroyed()) return;
                downloadHandle = null;
                updateDownloading = false;
                pendingInstallApk = null;
                renderUpdateBanner();
            }

            @Override
            public void onCancelled() {
                if (isFinishing() || isDestroyed()) return;
                downloadHandle = null;
                updateDownloading = false;
                renderUpdateBanner();
                Toast.makeText(MainActivity.this, "已取消下载", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message, String fallbackReleasePageUrl) {
                if (isFinishing() || isDestroyed()) return;
                downloadHandle = null;
                updateDownloading = false;
                updateErrorMsg = message == null || message.isEmpty() ? "下载失败" : message;
                if (fallbackReleasePageUrl != null && !fallbackReleasePageUrl.isEmpty()) {
                    updateFallbackUrl = fallbackReleasePageUrl;
                }
                renderUpdateBanner();
            }
        });
    }

    private void promptInstallPermission() {
        ui.confirm(
            "允许安装未知应用",
            "安装包已通过 SHA-256 校验。请在系统设置里允许「轻跳」安装应用，返回后会自动拉起安装。",
            "去授权",
            false,
            new Runnable() {
                @Override public void run() {
                    if (!AppUpdater.openInstallPermissionSettings(MainActivity.this)) {
                        Toast.makeText(MainActivity.this, "无法打开系统安装权限页", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        ).show();
    }

    private void renderUpdateBanner() {
        if (availableUpdate == null) {
            updateBanner.setVisibility(View.GONE);
            return;
        }
        updateBanner.setVisibility(View.VISIBLE);
        String ver = availableUpdate.versionName.isEmpty() ? "新版本" : "v" + availableUpdate.versionName;
        if (updateDownloading) {
            updateTitle.setText("正在下载 " + ver + (updatePercent > 0 ? " · " + updatePercent + "%" : ""));
            updateSub.setText("下载完成后自动校验 SHA-256");
            updateAction.setText("取消");
        } else if (pendingInstallApk != null) {
            updateTitle.setText(ver + " 已就绪");
            updateSub.setText("请允许「安装未知应用」后继续");
            updateAction.setText("去授权");
        } else if (updateErrorMsg != null && !updateErrorMsg.isEmpty()) {
            updateTitle.setText("有新版本 " + ver);
            updateSub.setText(updateErrorMsg);
            updateAction.setText("查看");
        } else {
            updateTitle.setText("有新版本 " + ver);
            updateSub.setText("校验 SHA-256 后本地安装");
            updateAction.setText("一键升级");
        }
        if (aboutItem != null) {
            TextView av = (TextView) aboutItem.trailing;
            av.setText("有新版本");
            av.setTextColor(Ui.TAG_INK);
            av.setTypeface(Ui.BOLD);
        }
    }

    private void maybeCheckUpdateSilently() {
        if (silentCheckStarted) return;
        silentCheckStarted = true;
        AppUpdater.checkIfDue(this, new AppUpdater.CheckCallback() {
            @Override
            public void onUpdateAvailable(UpdateInfo info) {
                if (isFinishing() || isDestroyed()) return;
                availableUpdate = info;
                updateFallbackUrl = info.releasePageUrl;
                renderUpdateBanner();
            }

            @Override
            public void onUpToDate(int currentVersionCode, String currentVersionName) {
                // 静默检查已是最新，不打扰
            }

            @Override
            public void onError(String message, String fallbackReleasePageUrl) {
                // 静默检查失败静默忽略，不打扰
            }
        });
    }

    // ---- 入口分组 ----

    private void buildGroups(LinearLayout page) {
        LinearLayout where = ui.addGroup(page, "在哪里跳");
        scopeItem = ui.item(Glyph.GRID, "生效范围", null, ui.value(""), go(PageActivity.SCOPE));
        where.addView(scopeItem.row);

        LinearLayout rec = ui.addGroup(page, "识别与录制");
        rec.addView(ui.item(Glyph.SEARCH, "内置规则", null, ui.value(Ui.builtinAppCount() + " 个"), go(PageActivity.BUILTIN)).row);
        recordItem = ui.item(Glyph.RECORD, "录制规则", null, ui.value("0 条"), go(PageActivity.RECORD));
        rec.addView(recordItem.row);
        // 通用识别模式暂不占首页位

        LinearLayout more = ui.addGroup(page, "权限与更多");
        permItem = ui.item(Glyph.SHIELD, "权限与保活", null, ui.value(""), go(PageActivity.PERM));
        more.addView(permItem.row);
        more.addView(ui.item(Glyph.DOTS, "更多", null, ui.value("测试 · 记录"), go(PageActivity.LAB)).row);
        aboutItem = ui.item(Glyph.INFO, "关于与隐私", null, ui.value("仅更新联网"), go(PageActivity.ABOUT));
        more.addView(aboutItem.row);
    }

    private View.OnClickListener go(final String page) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) { Device.page(MainActivity.this, page); }
        };
    }

    // ---- 刷新：只读内存状态 ----

    private void refresh(boolean animate) {
        boolean granted = Device.accessibilityOn(this);
        boolean ignored = Device.batteryIgnored(this);
        boolean enabled = scope.enabled();
        boolean all = scope.all();
        Set<String> picked = scope.selected();
        picked.remove(RuleBook.SELF);

        State s = !granted ? State.MISSING : !enabled ? State.PAUSED : !ignored ? State.PARTIAL : State.READY;
        int today = scope.todaySkips();

        int color = s == State.MISSING ? Ui.FIELD_MISS : s == State.PAUSED ? Ui.FIELD_OFF : Ui.FIELD_ON;
        setField(color, animate && shown != null);

        switch (s) {
            case MISSING:
                statusTitle.setText("还不能跳");
                statusSub.setText("开启无障碍和保活即可");
                break;
            case PARTIAL:
                statusTitle.setText("正在替你跳");
                statusSub.setText(boldNumber("今天 ", String.valueOf(today), " 次 · 未配保活"));
                break;
            case READY: {
                statusTitle.setText("正在替你跳");
                SpannableStringBuilder b = new SpannableStringBuilder("今天 ");
                boldAppend(b, String.valueOf(today));
                b.append(" 次，累计约省 ");
                boldAppend(b, Ui.saved(scope.savedSeconds()));
                // no trailing period
                statusSub.setText(b);
                break;
            }
            default:
                statusTitle.setText("歇着呢");
                statusSub.setText("推回拨杆即恢复");
                break;
        }

        // 任务条与拨杆
        tasks.setVisibility(s == State.MISSING || s == State.PARTIAL ? View.VISIBLE : View.GONE);
        if (s == State.MISSING) {
            taskAcc.root.setVisibility(View.VISIBLE);
            taskAcc.set(false, false, Ui.FIELD_MISS, "已开启");
            taskBat.root.setVisibility(View.VISIBLE);
            taskBat.set(ignored, false, Ui.FIELD_MISS, "已配置");
            restrictedNote.setVisibility(View.VISIBLE);
        } else if (s == State.PARTIAL) {
            taskAcc.root.setVisibility(View.GONE);
            taskBat.root.setVisibility(View.VISIBLE);
            taskBat.set(false, true, Ui.FIELD_ON, "已配置");
            restrictedNote.setVisibility(View.GONE);
        }
        LinearLayout.LayoutParams batLp = (LinearLayout.LayoutParams) taskBat.root.getLayoutParams();
        batLp.topMargin = s == State.PARTIAL ? 0 : ui.dp(10);
        taskBat.root.setLayoutParams(batLp);
        rocker.setVisibility(s == State.MISSING ? View.GONE : View.VISIBLE);
        rocker.setChecked(enabled, animate && shown != null);
        shown = s;

        // 信任行与入口数值（已加载应用列表时与 listable 取交集，避免显示超过总数的旧包计数）
        int total = catalog.isLoaded() ? listableCount() : -1;
        int pickedCount = catalog.isLoaded() ? pickedListableCount(picked) : picked.size();
        bindTrustScope(all, pickedCount);
        TextView sv = (TextView) scopeItem.trailing;
        if (all) {
            scopeItem.desc.setVisibility(View.GONE);
            int allTotal = catalog.isLoaded() ? countAllMode() : -1;
            sv.setText(allTotal >= 0 ? "全部 · " + allTotal : "全部");
            sv.setTextColor(Ui.WARN);
            sv.setTypeface(Ui.BOLD);
        } else {
            scopeItem.desc.setVisibility(View.GONE);
            sv.setText(total >= 0 ? pickedCount + " / " + total : pickedCount + " 个");
            sv.setTextColor(pickedCount == 0 ? Ui.WARN : Ui.INK_2);
            sv.setTypeface(pickedCount == 0 ? Ui.BOLD : null);
        }
        ((TextView) recordItem.trailing).setText(RuleBook.customEntries().size() + " 条");
        int steps = Device.permSteps(this);
        TextView pv = (TextView) permItem.trailing;
        pv.setText(steps + " / 3");
        pv.setTextColor(steps < 3 ? Ui.WARN : Ui.INK_2);
        pv.setTypeface(steps < 3 ? Ui.BOLD : null);
    }

    private int pickedListableCount(Set<String> picked) {
        int n = 0;
        for (AppInfo a : catalog.listable()) if (!a.isSelf() && picked.contains(a.pkg)) n++;
        return n;
    }

    private int listableCount() {
        int n = 0;
        for (AppInfo a : catalog.listable()) if (!a.isSelf()) n++;
        return n;
    }

    private int countAllMode() {
        Set<String> ex = scope.excluded();
        int n = 0;
        for (AppInfo a : catalog.listable()) if (!a.isSelf() && !ex.contains(a.pkg)) n++;
        return n;
    }

    private static CharSequence boldNumber(String head, String num, String tail) {
        SpannableStringBuilder b = new SpannableStringBuilder(head);
        boldAppend(b, num);
        b.append(tail);
        return b;
    }

    private static void boldAppend(SpannableStringBuilder b, String s) {
        int st = b.length();
        b.append(s);
        b.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), st, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new android.text.style.ForegroundColorSpan(Ui.INK), st, b.length(),
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void setField(int color, boolean animate) {
        if (color == fieldColor && fieldAnim == null) {
            syncStatusBar();
            return;
        }
        if (fieldAnim != null) fieldAnim.cancel();
        int from = fieldColor;
        fieldColor = color;
        if (!animate) {
            fieldBg.setColor(color);
            fieldAnim = null;
            syncStatusBar();
            return;
        }
        final int target = color;
        fieldAnim = Ui.tint(field, fieldBg, from, color, new Runnable() {
            @Override public void run() { syncStatusBar(); }
        });
        fieldAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator a) {
                fieldAnim = null;
                fieldBg.setColor(target);
                syncStatusBar();
            }
        });
    }

    /** 色场还在屏幕顶部时状态栏跟色场同色，滚出去后换回页面底色。 */
    private void syncStatusBar() {
        int y = scroll.getScrollY();
        int h = field.getHeight();
        int c;
        if (fieldAnim != null) {
            Object v = fieldAnim.getAnimatedValue();
            c = v instanceof Integer ? (Integer) v : fieldColor;
        } else {
            c = fieldColor;
        }
        getWindow().setStatusBarColor(h > 0 && y > h - ui.dp(40) ? Ui.BG : c);
    }

    @Override
    protected void onResume() {
        super.onResume();
        catalog.addListener(this);
        catalog.ensureLoaded();
        refresh(true);
        if (pendingInstallApk != null && AppUpdater.canInstallPackages(this)) {
            File f = pendingInstallApk;
            pendingInstallApk = null;
            renderUpdateBanner();
            AppUpdater.installApk(this, f);
        }
        maybeCheckUpdateSilently();
    }

    @Override
    protected void onPause() {
        super.onPause();
        catalog.removeListener(this);
    }

    @Override
    protected void onDestroy() {
        if (downloadHandle != null) {
            downloadHandle.cancel();
            downloadHandle = null;
        }
        super.onDestroy();
    }

    @Override
    public void onCatalogChanged() {
        refresh(false);
    }
}