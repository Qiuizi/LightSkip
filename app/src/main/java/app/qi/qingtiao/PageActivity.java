package app.qi.qingtiao;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 所有二级页的载体：按 EXTRA_PAGE 分发到具体的 Page。
 * 顶部是返回导航栏（标题在内容滚过 60dp 后淡入），下面是页面自己的内容。
 */
public final class PageActivity extends Activity {
    static final String EXTRA_PAGE = "page";
    static final String EXTRA_PKG = "pkg";

    static final String SCOPE = "scope";
    static final String RECORD = "record";
    static final String PERM = "perm";
    static final String BUILTIN = "builtin";
    static final String GENERIC = "generic";
    static final String LAB = "lab";
    static final String IMPORT = "import";
    static final String HITS = "hits";
    static final String ABOUT = "about";

    Ui ui;
    Scope scope;
    AppCatalog catalog;
    Ui.NavBar nav;
    private Page page;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new Ui(this);
        scope = new Scope(this);
        catalog = AppCatalog.get(this);
        catalog.ensureLoaded();

        String name = getIntent().getStringExtra(EXTRA_PAGE);
        page = create(name == null ? "" : name);
        ui.bars(page.tint());

        LinearLayout root = ui.frame(true);
        root.setBackgroundColor(Ui.BG);
        nav = ui.navBar(page.title(), new Runnable() {
            @Override public void run() { finish(); }
        });
        nav.bar.setBackgroundColor(page.tint());
        root.addView(nav.bar, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View content = page.build();
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
        Ui.insetTop(nav.bar);
        Ui.insetBottom(root);
    }

    private Page create(String name) {
        if (SCOPE.equals(name)) return new ScopePage(this);
        if (RECORD.equals(name)) return new RecordPage(this);
        if (PERM.equals(name)) return new PermPage(this);
        if (BUILTIN.equals(name)) return new BuiltinPage(this);
        if (GENERIC.equals(name)) return new GenericPage(this);
        if (IMPORT.equals(name)) return new ImportPage(this);
        if (HITS.equals(name)) return new HitsPage(this);
        if (ABOUT.equals(name)) return new AboutPage(this);
        return new LabPage(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        page.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        page.onPause();
    }

    @Override
    protected void onDestroy() {
        page.onDestroy();
        super.onDestroy();
    }

    /** 二级页基类：持有公共依赖，提供「标题 + 说明 + 可滚动正文」脚手架。 */
    abstract static class Page {
        final PageActivity a;
        final Ui ui;
        final Scope scope;
        final AppCatalog catalog;

        Page(PageActivity a) {
            this.a = a;
            this.ui = a.ui;
            this.scope = a.scope;
            this.catalog = a.catalog;
        }

        abstract String title();
        abstract View build();
        int tint() { return Ui.BG; }
        void onResume() {}
        void onPause() {}
        void onDestroy() {}

        /** 可滚动正文：32sp 大标题 + 14sp 说明；返回正文容器。 */
        LinearLayout scrollPage(String desc) {
            ScrollView s = ui.scroller();
            final LinearLayout body = ui.frame(true);
            ui.pad(body, 0, 0, 0, 32);
            s.addView(body);
            body.addView(header(desc));
            s.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                @Override public void onScrollChange(View v, int x, int y, int ox, int oy) {
                    a.nav.reveal(y > ui.dp(60));
                }
            });
            body.setTag(s);
            return body;
        }

        LinearLayout header(String desc) {
            LinearLayout h = ui.frame(true);
            ui.pad(h, 20, 4, 20, 4);
            h.addView(ui.pageTitle(title()));
            if (desc != null) {
                TextView d = ui.pageDesc(desc);
                ui.add(h, d, 6);
            }
            return h;
        }

        /** scrollPage 返回的正文容器所在的 ScrollView。 */
        static View root(LinearLayout body) {
            return (View) body.getTag();
        }

        void toast(String s) {
            android.widget.Toast.makeText(a, s, android.widget.Toast.LENGTH_SHORT).show();
        }

        /** 页脚小字说明（左右 24）。 */
        TextView footnote(LinearLayout body, String s, float top) {
            TextView t = ui.text(s, 12, Ui.INK_3);
            t.setLineSpacing(0, 1.3f);
            ui.add(body, t, top, 24, 0);
            return t;
        }
    }
}
