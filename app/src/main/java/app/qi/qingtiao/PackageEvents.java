package app.qi.qingtiao;

/** 装卸包广播的纯逻辑判定，供 PackageChangeReceiver 使用。 */
final class PackageEvents {
    static final String ADDED = "android.intent.action.PACKAGE_ADDED";
    static final String REMOVED = "android.intent.action.PACKAGE_REMOVED";
    static final String REPLACED = "android.intent.action.PACKAGE_REPLACED";

    enum Change { ADDED, REMOVED, UPDATED, IGNORE }

    private PackageEvents() {}

    /**
     * 覆盖安装时系统会依次发 REMOVED(replacing) → ADDED(replacing) → REPLACED，
     * 只把最后的 REPLACED 当成一次更新，前两条忽略。
     */
    static Change decide(String action, boolean replacing, String pkg) {
        if (pkg == null || pkg.isEmpty() || action == null) return Change.IGNORE;
        if (ADDED.equals(action)) return replacing ? Change.IGNORE : Change.ADDED;
        if (REMOVED.equals(action)) return replacing ? Change.IGNORE : Change.REMOVED;
        if (REPLACED.equals(action)) return Change.UPDATED;
        return Change.IGNORE;
    }
}
