package app.qi.qingtiao;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 已安装应用目录：后台单线程扫描、常驻内存缓存、装卸包广播增量刷新、图标异步缩放 + LruCache。
 * 界面层和 SkipService 共用一个进程内单例。所有 PackageManager 扫描都在后台线程；
 * 读方法（all/listable/locked/find/...）只读内存快照，可在主线程调用。
 * 监听器回调都在主线程。
 */
final class AppCatalog implements PackageChangeReceiver.Listener {
    interface Listener { void onCatalogChanged(); }
    interface IconCallback { void onIcon(String pkg, Bitmap icon); }

    private static AppCatalog instance;

    static synchronized AppCatalog get(Context context) {
        if (instance == null) instance = new AppCatalog(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService scanner = Executors.newSingleThreadExecutor();
    private final ExecutorService iconLoader = Executors.newSingleThreadExecutor();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private final LruCache<String, Bitmap> icons;

    private volatile Map<String, AppInfo> apps = Collections.emptyMap();
    private volatile Set<String> homes = Collections.emptySet();
    private volatile Set<String> imes = Collections.emptySet();
    private volatile boolean loaded;
    private boolean loading;

    private AppCatalog(Context app) {
        this.app = app;
        int kb = (int) Math.min(8 * 1024, Runtime.getRuntime().maxMemory() / 1024 / 16);
        icons = new LruCache<String, Bitmap>(kb) {
            @Override protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
        PackageChangeReceiver.register(app, this);
    }

    void addListener(Listener l) { if (l != null) listeners.addIfAbsent(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    boolean isLoaded() { return loaded; }

    /** 首次使用时触发后台扫描；已加载或正在加载则什么也不做。 */
    void ensureLoaded() {
        synchronized (this) {
            if (loaded || loading) return;
        }
        refresh();
    }

    /** 强制后台全量重扫（一般不需要，装卸包会自动增量刷新）。 */
    void refresh() {
        synchronized (this) { loading = true; }
        scanner.execute(new Runnable() {
            @Override public void run() {
                Set<String> h = queryHomes();
                Set<String> i = queryImes();
                Set<String> launchable = queryLaunchable();
                Map<String, AppInfo> next = new HashMap<String, AppInfo>();
                PackageManager pm = app.getPackageManager();
                List<ApplicationInfo> installed;
                try {
                    installed = pm.getInstalledApplications(0);
                } catch (RuntimeException e) {
                    installed = Collections.emptyList();
                }
                for (ApplicationInfo info : installed) {
                    next.put(info.packageName, toInfo(pm, info, h, i, launchable));
                }
                homes = Collections.unmodifiableSet(h);
                imes = Collections.unmodifiableSet(i);
                apps = Collections.unmodifiableMap(next);
                synchronized (AppCatalog.this) {
                    loaded = true;
                    loading = false;
                }
                notifyChanged();
            }
        });
    }

    /** 所有已安装应用（含系统应用），无序。 */
    List<AppInfo> all() { return new ArrayList<AppInfo>(apps.values()); }

    /** 「生效范围」可勾选列表，已排序。 */
    List<AppInfo> listable() { return AppInfo.listableOf(all()); }

    /** 「永不点击」组。 */
    List<AppInfo> locked() { return AppInfo.lockedOf(all()); }

    /** 在可勾选列表里按名称/包名搜索。 */
    List<AppInfo> search(String query) { return AppInfo.filter(listable(), query); }

    AppInfo find(String pkg) { return pkg == null ? null : apps.get(pkg); }

    Set<String> installedPackages() { return new HashSet<String>(apps.keySet()); }

    boolean isHome(String pkg) { return homes.contains(pkg); }
    boolean isIme(String pkg) { return imes.contains(pkg); }

    /** 未加载完成或不认识的包返回 null，调用方自行兜底。 */
    Boolean isSystem(String pkg) {
        AppInfo a = find(pkg);
        return a == null ? null : a.system;
    }

    /**
     * 异步取缩放到 sizePx 的图标；命中缓存时同步回调。回调在主线程，icon 可能为 null。
     */
    void loadIcon(final String pkg, final int sizePx, final IconCallback cb) {
        if (pkg == null || cb == null || sizePx <= 0) return;
        final String key = pkg + "@" + sizePx;
        Bitmap hit = icons.get(key);
        if (hit != null) {
            cb.onIcon(pkg, hit);
            return;
        }
        iconLoader.execute(new Runnable() {
            @Override public void run() {
                Bitmap bmp = null;
                try {
                    Drawable d = app.getPackageManager().getApplicationIcon(pkg);
                    bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
                    Canvas c = new Canvas(bmp);
                    d.setBounds(0, 0, sizePx, sizePx);
                    d.draw(c);
                    icons.put(key, bmp);
                } catch (Exception ignored) {
                    bmp = null;
                }
                final Bitmap result = bmp;
                main.post(new Runnable() {
                    @Override public void run() { cb.onIcon(pkg, result); }
                });
            }
        });
    }

    @Override
    public void onPackageChanged(final String pkg, final PackageEvents.Change change) {
        evictIcons(pkg);
        scanner.execute(new Runnable() {
            @Override public void run() {
                Map<String, AppInfo> next = new HashMap<String, AppInfo>(apps);
                if (change == PackageEvents.Change.REMOVED) {
                    next.remove(pkg);
                } else {
                    PackageManager pm = app.getPackageManager();
                    Set<String> h = queryHomes();
                    Set<String> i = queryImes();
                    try {
                        ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                        Intent launch = pm.getLaunchIntentForPackage(pkg);
                        Set<String> launchable = new HashSet<String>();
                        if (launch != null) launchable.add(pkg);
                        next.put(pkg, toInfo(pm, info, h, i, launchable));
                    } catch (PackageManager.NameNotFoundException gone) {
                        next.remove(pkg);
                    } catch (RuntimeException ignored) {
                        return;
                    }
                    homes = Collections.unmodifiableSet(h);
                    imes = Collections.unmodifiableSet(i);
                }
                apps = Collections.unmodifiableMap(next);
                notifyChanged();
            }
        });
    }

    private void evictIcons(String pkg) {
        String prefix = pkg + "@";
        for (String k : new ArrayList<String>(icons.snapshot().keySet())) {
            if (k.startsWith(prefix)) icons.remove(k);
        }
    }

    private void notifyChanged() {
        main.post(new Runnable() {
            @Override public void run() {
                for (Listener l : listeners) l.onCatalogChanged();
            }
        });
    }

    private static AppInfo toInfo(PackageManager pm, ApplicationInfo info, Set<String> homes,
            Set<String> imes, Set<String> launchable) {
        String pkg = info.packageName;
        boolean system = (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0
            && (info.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
        String label;
        try {
            label = RuleBook.SELF.equals(pkg) ? "内置测试页" : String.valueOf(pm.getApplicationLabel(info));
        } catch (RuntimeException e) {
            label = pkg;
        }
        return new AppInfo(pkg, label, system, homes.contains(pkg), imes.contains(pkg), launchable.contains(pkg));
    }

    private Set<String> queryHomes() {
        return activityPackages(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
    }

    private Set<String> queryLaunchable() {
        return activityPackages(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER));
    }

    private Set<String> activityPackages(Intent intent) {
        Set<String> out = new HashSet<String>();
        try {
            List<ResolveInfo> list = app.getPackageManager().queryIntentActivities(intent, 0);
            if (list != null) {
                for (ResolveInfo ri : list) {
                    if (ri.activityInfo != null) out.add(ri.activityInfo.packageName);
                }
            }
        } catch (RuntimeException ignored) { }
        return out;
    }

    private Set<String> queryImes() {
        Set<String> out = new HashSet<String>();
        try {
            InputMethodManager imm = (InputMethodManager) app.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                for (InputMethodInfo info : imm.getInputMethodList()) out.add(info.getPackageName());
            }
        } catch (RuntimeException ignored) { }
        return out;
    }
}
