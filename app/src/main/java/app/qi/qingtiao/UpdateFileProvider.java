package app.qi.qingtiao;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Minimal zero-dependency read-only ContentProvider that exposes only verified
 * update APKs inside <cacheDir>/updates/ to the Android package installer.
 */
public final class UpdateFileProvider extends ContentProvider {
    static final String UPDATES_SUBDIR = "updates";
    static final String APK_MIME = "application/vnd.android.package-archive";
    private static final Pattern SAFE_APK_NAME = Pattern.compile("^[A-Za-z0-9._-]+\\.apk$");

    static Uri uriFor(Context context, File apkFile) {
        String name = apkFile.getName();
        if (!isSafeApkFileName(name)) {
            throw new IllegalArgumentException("Unsafe APK filename: " + name);
        }
        return new Uri.Builder()
                .scheme("content")
                .authority(context.getPackageName() + ".updater")
                .appendPath(name)
                .build();
    }

    static boolean isSafeApkFileName(String name) {
        return name != null
                && !name.contains("..")
                && !name.contains("/")
                && !name.contains("\\")
                && SAFE_APK_NAME.matcher(name).matches();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return APK_MIME;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File file;
        try {
            file = resolveApkFile(uri);
        } catch (FileNotFoundException e) {
            return new MatrixCursor(new String[0]);
        }
        String[] cols = projection != null && projection.length > 0
                ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
                row[i] = file.getName();
            } else if (OpenableColumns.SIZE.equals(cols[i])) {
                row[i] = file.length();
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && !"r".equals(mode)) {
            throw new SecurityException("Read-only provider");
        }
        File file = resolveApkFile(uri);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Read-only");
    }

    private File resolveApkFile(Uri uri) throws FileNotFoundException {
        Context ctx = getContext();
        if (ctx == null || uri == null) {
            throw new FileNotFoundException("No context or uri");
        }
        String name = uri.getLastPathSegment();
        if (!isSafeApkFileName(name)) {
            throw new FileNotFoundException("Invalid APK path");
        }
        File dir = new File(ctx.getCacheDir(), UPDATES_SUBDIR);
        File target = new File(dir, name);
        try {
            String dirCanon = dir.getCanonicalPath() + File.separator;
            String fileCanon = target.getCanonicalPath();
            if (!fileCanon.startsWith(dirCanon) || !target.isFile()) {
                throw new FileNotFoundException("APK not found");
            }
        } catch (IOException e) {
            throw new FileNotFoundException("Failed to resolve APK");
        }
        return target;
    }
}
