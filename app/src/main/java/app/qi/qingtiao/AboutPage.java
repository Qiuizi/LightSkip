package app.qi.qingtiao;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.Locale;
import java.util.Set;

/** 关于与隐私：「本机说明」规格表，每一行都和程序实际行为一致；版本与检查更新；带确认的清除跳过记录。 */
final class AboutPage extends PageActivity.Page {
    private enum UpdateState { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, NEED_PERM, ERROR }

    private LinearLayout table;
    private Ui.Item updateItem;
    private UpdateState updateState = UpdateState.IDLE;
    private UpdateInfo availableUpdate;
    private AppUpdater.DownloadHandle downloadHandle;
    private File pendingInstallApk;
    private String lastFallbackUrl = UpdateInfo.DEFAULT_RELEASE_PAGE;
    private String lastErrorMsg = "";
    private int downloadPercent;
    private boolean destroyed;

    AboutPage(PageActivity a) { super(a); }

    @Override String title() { return "关于与隐私"; }

    @Override int tint() { return Ui.FIELD_SOFT; }

    @Override
    View build() {
        LinearLayout body = scrollPage("轻跳在你手机上做什么、不做什么");
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
        footnote(body, "*开屏广告一般 3–5 秒，这里按 3 秒估算", 10);

        LinearLayout g = ui.addGroup(body, "关于与更新");
        g.addView(ui.item(Glyph.PHONE, "当前版本", "纯原生轻量开屏跳过工具",
            ui.value("v" + Ui.version(a)), null).row);

        updateItem = ui.item(Glyph.DOWNLOAD, "检查更新", "当前 v" + Ui.version(a) + " · 仅更新时联网",
            ui.value("检查"), new View.OnClickListener() {
                @Override public void onClick(View v) { onUpdateRowClick(); }
            });
        g.addView(updateItem.row);
        renderUpdateRow();

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

    private void onUpdateRowClick() {
        switch (updateState) {
            case CHECKING:
                return;
            case DOWNLOADING:
                if (downloadHandle != null) downloadHandle.cancel();
                return;
            case AVAILABLE:
                if (availableUpdate != null) startDownload(availableUpdate);
                else triggerCheck();
                return;
            case NEED_PERM:
                if (pendingInstallApk != null && AppUpdater.canInstallPackages(a)) {
                    File f = pendingInstallApk;
                    pendingInstallApk = null;
                    AppUpdater.installApk(a, f);
                    updateState = UpdateState.AVAILABLE;
                    renderUpdateRow();
                } else {
                    promptInstallPermission();
                }
                return;
            case ERROR:
                showErrorSheet();
                return;
            default:
                triggerCheck();
                return;
        }
    }

    private void triggerCheck() {
        updateState = UpdateState.CHECKING;
        lastErrorMsg = "";
        renderUpdateRow();
        AppUpdater.checkNow(a, new AppUpdater.CheckCallback() {
            @Override
            public void onUpdateAvailable(UpdateInfo info) {
                if (destroyed) return;
                availableUpdate = info;
                lastFallbackUrl = info.releasePageUrl;
                updateState = UpdateState.AVAILABLE;
                renderUpdateRow();
            }

            @Override
            public void onUpToDate(int currentVersionCode, String currentVersionName) {
                if (destroyed) return;
                availableUpdate = null;
                updateState = UpdateState.UP_TO_DATE;
                renderUpdateRow();
            }

            @Override
            public void onError(String message, String fallbackReleasePageUrl) {
                if (destroyed) return;
                lastErrorMsg = message == null || message.isEmpty() ? "检查更新失败" : message;
                if (fallbackReleasePageUrl != null && !fallbackReleasePageUrl.isEmpty()) {
                    lastFallbackUrl = fallbackReleasePageUrl;
                }
                updateState = UpdateState.ERROR;
                renderUpdateRow();
            }
        });
    }

    private void startDownload(final UpdateInfo info) {
        updateState = UpdateState.DOWNLOADING;
        downloadPercent = 0;
        lastErrorMsg = "";
        lastFallbackUrl = info.releasePageUrl;
        renderUpdateRow();
        downloadHandle = AppUpdater.downloadAndInstall(a, info, new AppUpdater.DownloadCallback() {
            @Override
            public void onProgress(long bytesRead, long totalBytes, int percent) {
                if (destroyed) return;
                downloadPercent = Math.max(0, Math.min(100, percent));
                renderUpdateRow();
            }

            @Override
            public void onNeedInstallPermission(File apkFile) {
                if (destroyed) return;
                downloadHandle = null;
                pendingInstallApk = apkFile;
                updateState = UpdateState.NEED_PERM;
                renderUpdateRow();
                promptInstallPermission();
            }

            @Override
            public void onReadyToInstall(File apkFile) {
                if (destroyed) return;
                downloadHandle = null;
                pendingInstallApk = null;
                updateState = UpdateState.AVAILABLE;
                renderUpdateRow();
            }

            @Override
            public void onCancelled() {
                if (destroyed) return;
                downloadHandle = null;
                updateState = availableUpdate != null ? UpdateState.AVAILABLE : UpdateState.IDLE;
                renderUpdateRow();
                toast("已取消下载");
            }

            @Override
            public void onError(String message, String fallbackReleasePageUrl) {
                if (destroyed) return;
                downloadHandle = null;
                lastErrorMsg = message == null || message.isEmpty() ? "下载失败" : message;
                if (fallbackReleasePageUrl != null && !fallbackReleasePageUrl.isEmpty()) {
                    lastFallbackUrl = fallbackReleasePageUrl;
                }
                updateState = UpdateState.ERROR;
                renderUpdateRow();
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
                    if (!AppUpdater.openInstallPermissionSettings(a)) {
                        toast("无法打开系统安装权限页");
                    }
                }
            }
        ).show();
    }

    private void showErrorSheet() {
        String msg = lastErrorMsg.isEmpty() ? "连接更新源失败，可以重试或直接用浏览器打开发布页。" : lastErrorMsg;
        ui.confirm(
            "更新未完成",
            msg + "\n\n点「浏览器打开」将跳转到 GitHub Releases 页面手动下载。",
            "浏览器打开",
            false,
            new Runnable() {
                @Override public void run() {
                    AppUpdater.openReleasePage(a, lastFallbackUrl);
                }
            }
        ).show();
    }

    private void renderUpdateRow() {
        if (updateItem == null) return;
        TextView tv = (TextView) updateItem.trailing;
        updateItem.desc.setVisibility(View.VISIBLE);
        switch (updateState) {
            case CHECKING:
                updateItem.desc.setText("正在连接 GitHub Releases 检查新版本…");
                tv.setText("检查中…");
                tv.setTextColor(Ui.INK_3);
                tv.setTypeface(null);
                break;
            case UP_TO_DATE:
                updateItem.desc.setText("当前 v" + Ui.version(a) + " 已是最新版本");
                tv.setText("已是最新");
                tv.setTextColor(Ui.TAG_INK);
                tv.setTypeface(Ui.BOLD);
                break;
            case AVAILABLE: {
                String ver = availableUpdate != null && !availableUpdate.versionName.isEmpty()
                    ? "v" + availableUpdate.versionName : "新版本";
                String size = availableUpdate != null && availableUpdate.sizeBytes > 0
                    ? " · " + formatSize(availableUpdate.sizeBytes) : "";
                updateItem.desc.setText("发现 " + ver + size + " · 校验 SHA-256 后安装");
                tv.setText("一键升级");
                tv.setTextColor(Ui.TAG_INK);
                tv.setTypeface(Ui.BOLD);
                break;
            }
            case DOWNLOADING: {
                String ver = availableUpdate != null && !availableUpdate.versionName.isEmpty()
                    ? "v" + availableUpdate.versionName : "新版本";
                updateItem.desc.setText("正在下载 " + ver + " · 点此取消");
                tv.setText(downloadPercent > 0 ? downloadPercent + "%" : "下载中…");
                tv.setTextColor(Ui.INK);
                tv.setTypeface(Ui.BOLD);
                break;
            }
            case NEED_PERM:
                updateItem.desc.setText("安装包已校验通过，请允许「安装未知应用」");
                tv.setText("去授权");
                tv.setTextColor(Ui.WARN);
                tv.setTypeface(Ui.BOLD);
                break;
            case ERROR:
                updateItem.desc.setText(lastErrorMsg.isEmpty() ? "检查失败 · 点此用浏览器打开" : lastErrorMsg);
                tv.setText("查看");
                tv.setTextColor(Ui.WARN);
                tv.setTypeface(Ui.BOLD);
                break;
            default:
                updateItem.desc.setText("当前 v" + Ui.version(a) + " · 仅检查更新时联网");
                tv.setText("检查");
                tv.setTextColor(Ui.INK_2);
                tv.setTypeface(null);
                break;
        }
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L * 1024L) {
            return Math.max(1L, bytes / 1024L) + " KB";
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void bindTable() {
        table.removeAllViews();
        table.addView(ui.kv("网络权限", "仅更新时联网", 22,
            "日常跳广告完全离线，无障碍服务绝不联网；仅检查更新与下载新版安装包时连接 GitHub，不上传任何本机数据。", Ui.FIELD_ON));
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

    @Override
    void onResume() {
        bindTable();
        if (pendingInstallApk != null && AppUpdater.canInstallPackages(a)) {
            File f = pendingInstallApk;
            pendingInstallApk = null;
            updateState = availableUpdate != null ? UpdateState.AVAILABLE : UpdateState.IDLE;
            renderUpdateRow();
            AppUpdater.installApk(a, f);
        }
    }

    @Override
    void onDestroy() {
        destroyed = true;
        if (downloadHandle != null) {
            downloadHandle.cancel();
            downloadHandle = null;
        }
    }
}