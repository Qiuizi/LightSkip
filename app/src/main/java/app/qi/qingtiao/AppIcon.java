package app.qi.qingtiao;

import android.graphics.Bitmap;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

/** 应用图标视图：36dp、圆角 10，异步从 AppCatalog 取缩放好的位图，按 tag 防止复用错位。 */
final class AppIcon {
    static final float SIZE_DP = 28f;
    private static final ColorMatrixColorFilter GREY;

    static {
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(0f);
        GREY = new ColorMatrixColorFilter(m);
    }

    private AppIcon() {}

    static ImageView create(final Ui ui) {
        ImageView v = new ImageView(ui.a);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setBackground(ui.shape(Ui.CHIP, 7));
        final float r = ui.dp(7);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        v.setClipToOutline(true);
        return v;
    }

    /** 绑定某个包的图标；locked=true 时去色并降到 45% 透明度。 */
    static void bind(final ImageView v, final String pkg, AppCatalog catalog, int sizePx, boolean locked) {
        v.setTag(pkg);
        v.setImageDrawable(null);
        v.setColorFilter(locked ? GREY : null);
        v.setAlpha(locked ? 0.45f : 1f);
        catalog.loadIcon(pkg, sizePx, new AppCatalog.IconCallback() {
            @Override public void onIcon(String p, Bitmap icon) {
                if (icon != null && p.equals(v.getTag())) v.setImageBitmap(icon);
            }
        });
    }
}
