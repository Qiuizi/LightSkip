package app.qi.qingtiao;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;

/**
 * 装卸包广播。Android 8+ 不允许清单静态注册这几条隐式广播，所以由 AppCatalog 在进程内动态注册，
 * 不需要任何新权限。思路参考 MIT 协议的 zfdang/Android-Touch-Helper，代码为自写。
 */
final class PackageChangeReceiver extends BroadcastReceiver {
    interface Listener { void onPackageChanged(String pkg, PackageEvents.Change change); }

    private final Listener listener;

    private PackageChangeReceiver(Listener listener) {
        this.listener = listener;
    }

    static PackageChangeReceiver register(Context context, Listener listener) {
        PackageChangeReceiver r = new PackageChangeReceiver(listener);
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            // 系统广播对 NOT_EXPORTED 接收器照样投递；不对其他 App 暴露。
            context.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(r, f);
        }
        return r;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        Uri data = intent.getData();
        String pkg = data == null ? null : data.getSchemeSpecificPart();
        boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
        PackageEvents.Change change = PackageEvents.decide(intent.getAction(), replacing, pkg);
        if (change != PackageEvents.Change.IGNORE && listener != null) {
            listener.onPackageChanged(pkg, change);
        }
    }
}
