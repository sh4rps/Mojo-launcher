package com.robin.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends Activity implements Apps.Listener {
    FrameLayout main;
    LinearLayout header, dock;
    TextView banner;
    Views.Pager pager;
    Views.Dots dots;
    FrameLayout drawer;
    GridView grid;
    EditText search;
    Hz.Ctl hz;
    int insetT, insetB, cols = 4, rows = 5;
    float drawerP;
    boolean drawerOpen;
    ValueAnimator dAnim;
    String sig = "";
    final List<Apps.Info> shown = new ArrayList<>();
    AppAdapter adapter;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setDecorFitsSystemWindows(false);
        WindowInsetsController wic = getWindow().getInsetsController();
        if (wic != null) wic.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        hz = new Hz.Ctl(getWindow(), this);
        build();
        Apps.listen(this);
        ensureDefaults();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
    }

    @Override protected void onDestroy() { Apps.unlisten(this); super.onDestroy(); }

    // ------------------------------------------------------------------ UI
    void build() {
        main = new Main(this);
        main.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets w) {
                Insets i = w.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                insetT = i.top; insetB = i.bottom;
                layoutAll();
                return WindowInsets.CONSUMED;
            }
        });

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        TextClock t = new TextClock(this);
        t.setFormat24Hour("HH:mm"); t.setFormat12Hour("h:mm");
        t.setTextSize(54); t.setTextColor(0xFFFFFFFF);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
        t.setShadowLayer(U.dp(4), 0, U.dp(1), 0x66000000);
        TextClock d = new TextClock(this);
        d.setFormat24Hour("EEEE, d MMMM"); d.setFormat12Hour("EEEE, d MMMM");
        d.setTextSize(15); d.setTextColor(0xFFFFFFFF);
        d.setShadowLayer(U.dp(3), 0, U.dp(1), 0x66000000);
        header.addView(t); header.addView(d);
        main.addView(header, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));

        banner = U.tv(this, "Finish setup ›", 13, 0xFFFFFFFF, true);
        banner.setBackground(U.round(0x992F2F36, 16));
        banner.setPadding(U.dp(14), U.dp(7), U.dp(14), U.dp(7));
        banner.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startActivity(new Intent(HomeActivity.this, SettingsActivity.class)); } });
        main.addView(banner, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END));

        pager = new Views.Pager(this);
        main.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        dots = new Views.Dots(this);
        main.addView(dots, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        pager.onPos = new Runnable() { public void run() { dots.pos = pager.pos; dots.invalidate(); } };

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(U.round(0x40FFFFFF, 30));
        main.addView(dock, new FrameLayout.LayoutParams(-1, U.dp(88), Gravity.BOTTOM));

        buildDrawer();
        setContentView(main);
    }

    void layoutAll() {
        FrameLayout.LayoutParams hl = (FrameLayout.LayoutParams) header.getLayoutParams();
        hl.setMargins(U.dp(26), insetT + U.dp(40), 0, 0);
        header.setLayoutParams(hl);
        FrameLayout.LayoutParams bl = (FrameLayout.LayoutParams) banner.getLayoutParams();
        bl.setMargins(0, insetT + U.dp(52), U.dp(20), 0);
        banner.setLayoutParams(bl);
        FrameLayout.LayoutParams dl = (FrameLayout.LayoutParams) dock.getLayoutParams();
        dl.setMargins(U.dp(14), 0, U.dp(14), insetB + U.dp(10));
        dock.setLayoutParams(dl);
        FrameLayout.LayoutParams ol = (FrameLayout.LayoutParams) dots.getLayoutParams();
        ol.bottomMargin = insetB + U.dp(10) + U.dp(88) + U.dp(4);
        dots.setLayoutParams(ol);
        int top = insetT + U.dp(40) + U.dp(112);
        int bottom = insetB + U.dp(10) + U.dp(88) + U.dp(22);
        FrameLayout.LayoutParams pl = (FrameLayout.LayoutParams) pager.getLayoutParams();
        pl.setMargins(U.dp(8), top, U.dp(8), bottom);
        pager.setLayoutParams(pl);
        drawer.setPadding(U.dp(12), insetT + U.dp(14), U.dp(12), insetB + U.dp(8));
        main.post(new Runnable() { public void run() { rebuild(true); } });
    }

    List<Apps.Info> homeApps() {
        List<String> hidden = Prefs.list("hidden"), dk = Prefs.list("dock");
        List<Apps.Info> out = new ArrayList<>();
        if (Prefs.drawerMode()) {
            for (String k : Prefs.list("home")) { Apps.Info a = Apps.find(k); if (a != null && !hidden.contains(k)) out.add(a); }
        } else {
            for (Apps.Info a : Apps.all) if (!hidden.contains(a.key()) && !dk.contains(a.key())) out.add(a);
        }
        return out;
    }

    void rebuild(boolean force) {
        String s = Prefs.drawerMode() + "|" + Prefs.labels() + "|" + Apps.all.size() + "|" + Prefs.list("hidden") + Prefs.list("dock") + Prefs.list("home") + pager.getHeight();
        if (!force && s.equals(sig)) return;
        sig = s;
        if (pager.getHeight() <= 0) { main.post(new Runnable() { public void run() { rebuild(true); } }); return; }
        rows = Math.max(3, pager.getHeight() / U.dp(104));
        int per = cols * rows;
        List<Apps.Info> l = homeApps();
        int pages = Math.max(1, (l.size() + per - 1) / per);
        float keep = Math.min(pager.pos, pages - 1);
        pager.removeAllViews();
        for (int p = 0; p < pages; p++) {
            Views.Grid g = new Views.Grid(this, cols, rows);
            for (int i = p * per; i < Math.min(l.size(), (p + 1) * per); i++) g.addView(cell(l.get(i), true));
            pager.addView(g, new FrameLayout.LayoutParams(-1, -1));
        }
        dots.count = pages;
        pager.setPos(Math.round(keep));
        // dock
        dock.removeAllViews();
        for (String k : Prefs.list("dock")) {
            Apps.Info a = Apps.find(k);
            if (a == null) continue;
            View c = cell(a, false);
            dock.addView(c, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        refreshDrawerList();
        updateBanner();
    }

    View cell(final Apps.Info a, boolean label) {
        final Views.Cell c = new Views.Cell(this);
        c.bind(a, label && Prefs.labels(), 0xFFFFFFFF);
        c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { launch(a, v); } });
        c.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { menu(a, v); return true; } });
        return c;
    }

    void launch(Apps.Info a, View v) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Bundle o = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight()).toBundle();
            startActivity(i, o);
        } catch (Throwable e) { Toast.makeText(this, "Can't open " + a.label, Toast.LENGTH_SHORT).show(); }
    }

    void menu(final Apps.Info a, View anchor) {
        PopupMenu m = new PopupMenu(this, anchor);
        final boolean inDock = Prefs.list("dock").contains(a.key());
        m.getMenu().add(0, 1, 0, "App info");
        m.getMenu().add(0, 2, 1, "Uninstall");
        m.getMenu().add(0, 3, 2, "Hide app");
        m.getMenu().add(0, 4, 3, inDock ? "Remove from dock" : "Add to dock");
        if (Prefs.drawerMode()) m.getMenu().add(0, 5, 4, Prefs.list("home").contains(a.key()) ? "Remove from home" : "Add to home");
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            public boolean onMenuItemClick(MenuItem it) {
                switch (it.getItemId()) {
                    case 1: startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))); break;
                    case 2: startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))); break;
                    case 3: Prefs.toggleIn("hidden", a.key()); break;
                    case 4:
                        List<String> d = Prefs.list("dock");
                        if (d.contains(a.key())) d.remove(a.key());
                        else if (d.size() >= 5) { Toast.makeText(HomeActivity.this, "Dock is full", Toast.LENGTH_SHORT).show(); break; }
                        else d.add(a.key());
                        Prefs.list("dock", d);
                        break;
                    case 5: Prefs.toggleIn("home", a.key()); break;
                }
                rebuild(false);
                return true;
            }
        });
        m.show();
    }

    // ------------------------------------------------------------------ defaults
    void ensureDefaults() {
        if (Apps.all.isEmpty()) return;
        if (!Prefs.b("dock_init", false)) {
            List<String> d = new ArrayList<>();
            Intent[] ints = {new Intent(Intent.ACTION_DIAL), Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
                    Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER), new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)};
            for (Intent i : ints) { Apps.Info a = Apps.resolve(this, i); if (a != null && !d.contains(a.key())) d.add(a.key()); }
            Prefs.list("dock", d);
            Prefs.setB("dock_init", true);
        }
        if (!Prefs.b("home_init", false)) {
            List<String> h = new ArrayList<>();
            String[] cats = {Intent.CATEGORY_APP_CONTACTS, Intent.CATEGORY_APP_GALLERY, Intent.CATEGORY_APP_EMAIL, Intent.CATEGORY_APP_CALENDAR,
                    Intent.CATEGORY_APP_MAPS, Intent.CATEGORY_APP_MUSIC, Intent.CATEGORY_APP_CALCULATOR};
            for (String c : cats) {
                Apps.Info a = Apps.resolve(this, Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, c));
                if (a != null && !h.contains(a.key())) h.add(a.key());
            }
            Apps.Info st = Apps.resolve(this, new Intent(Settings.ACTION_SETTINGS));
            if (st != null && !h.contains(st.key())) h.add(st.key());
            Prefs.list("home", h);
            Prefs.setB("home_init", true);
        }
    }

    @Override public void onApps() { ensureDefaults(); rebuild(true); }

    // ------------------------------------------------------------------ drawer
    void buildDrawer() {
        drawer = new FrameLayout(this);
        drawer.setBackgroundColor(U.night(this) ? 0xD90E0F12 : 0xD9F4F4F7);
        drawer.setVisibility(View.GONE);
        drawer.setClickable(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        search = new EditText(this);
        search.setHint("Search apps");
        search.setSingleLine();
        search.setTextColor(U.text(this));
        search.setHintTextColor(U.sub(this));
        search.setBackground(U.round(U.tile(this), 22));
        search.setPadding(U.dp(18), U.dp(10), U.dp(18), U.dp(10));
        search.setTextSize(15);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { refreshDrawerList(); }
            public void afterTextChanged(Editable s) { }
        });
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                if (!shown.isEmpty()) launch(shown.get(0), search);
                return true;
            }
        });
        col.addView(search, new LinearLayout.LayoutParams(-1, U.dp(46)));
        grid = new GridView(this);
        grid.setNumColumns(cols);
        grid.setVerticalSpacing(U.dp(6));
        grid.setSelector(android.R.color.transparent);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setFastScrollEnabled(true);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(-1, 0, 1f);
        gl.topMargin = U.dp(12);
        col.addView(grid, gl);
        drawer.addView(col, new FrameLayout.LayoutParams(-1, -1));
        main.addView(drawer, new FrameLayout.LayoutParams(-1, -1));
    }

    void refreshDrawerList() {
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<String> hidden = Prefs.list("hidden");
        shown.clear();
        for (Apps.Info a : Apps.all) {
            if (hidden.contains(a.key())) continue;
            if (q.isEmpty() || a.label.toLowerCase(Locale.ROOT).contains(q)) shown.add(a);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    class AppAdapter extends BaseAdapter {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View v, ViewGroup p) {
            Views.Cell c = (Views.Cell) v;
            final Apps.Info a = shown.get(i);
            if (c == null) {
                c = new Views.Cell(HomeActivity.this);
                c.setLayoutParams(new AbsListView.LayoutParams(-1, U.dp(100)));
            }
            c.bind(a, true, U.text(HomeActivity.this));
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View x) { launch(a, x); } });
            c.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View x) { menu(a, x); return true; } });
            return c;
        }
    }

    void setDrawer(float p) {
        drawerP = Math.max(0f, Math.min(1f, p));
        int h = main.getHeight();
        drawer.setVisibility(drawerP > 0.001f ? View.VISIBLE : View.GONE);
        drawer.setTranslationY((1f - drawerP) * h * 0.9f);
        drawer.setAlpha(Math.min(1f, drawerP * 1.8f));
        float f = 1f - drawerP;
        float sc = 1f - 0.07f * drawerP;
        View[] vs = {pager, header, dock, dots, banner};
        for (View v : vs) { v.setAlpha(f); v.setScaleX(sc); v.setScaleY(sc); }
    }

    void settle(final boolean open) {
        if (dAnim != null) dAnim.cancel();
        final float from = drawerP, to = open ? 1f : 0f;
        hz.boost();
        dAnim = ValueAnimator.ofFloat(0, 1);
        dAnim.setDuration(open ? 360 : 300);
        dAnim.setInterpolator(open ? U.SPRING : new android.view.animation.DecelerateInterpolator(1.6f));
        dAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) { setDrawer(from + (to - from) * (Float) a.getAnimatedValue()); }
        });
        dAnim.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                setDrawer(to);
                drawerOpen = open;
                blur(open);
                if (!open) { search.setText(""); search.clearFocus(); hideKb(); grid.setSelection(0); }
            }
        });
        dAnim.start();
    }

    void blur(boolean on) {
        try { if (Build.VERSION.SDK_INT >= 31) getWindow().setBackgroundBlurRadius(on ? U.dp(20) : 0); } catch (Throwable e) { }
    }

    void hideKb() {
        try { ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(main.getWindowToken(), 0); } catch (Throwable e) { }
    }

    // ------------------------------------------------------------------ gestures
    class Main extends FrameLayout {
        static final int NONE = 0, OPEN = 1, CLOSE = 2, PAGE = 3, PULL = 4;
        float dx0, dy0, startPos;
        int mode;
        VelocityTracker vt;
        final int slop = ViewConfiguration.get(HomeActivity.this).getScaledTouchSlop();
        boolean topRight;

        Main(Context c) { super(c); }

        @Override public boolean dispatchTouchEvent(MotionEvent e) {
            hz.boost();
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                dx0 = e.getX(); dy0 = e.getY(); mode = NONE; startPos = pager.pos;
                topRight = e.getY() < insetT + U.dp(120) && e.getX() > getWidth() * 0.5f;
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain();
                if (dAnim != null) dAnim.cancel();
                if (pager.anim != null) pager.anim.cancel();
            }
            if (vt != null) vt.addMovement(e);
            return super.dispatchTouchEvent(e);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (e.getActionMasked() != MotionEvent.ACTION_MOVE || mode != NONE) return mode != NONE;
            float dx = e.getX() - dx0, dy = e.getY() - dy0;
            if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
                if (drawerOpen) {
                    if (dy > 0 && !grid.canScrollVertically(-1)) { mode = CLOSE; return true; }
                    return false;
                }
                mode = dy < 0 ? OPEN : PULL;
                return true;
            }
            if (!drawerOpen && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) { mode = PAGE; return true; }
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float dx = e.getX() - dx0, dy = e.getY() - dy0;
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) return true;
            if (a == MotionEvent.ACTION_MOVE) {
                if (mode == NONE) onInterceptTouchEvent(e);
                int h = Math.max(1, getHeight());
                if (mode == OPEN) setDrawer(-dy / (h * 0.55f));
                else if (mode == CLOSE) setDrawer(1f - dy / (h * 0.55f));
                else if (mode == PAGE) pager.setPos(startPos - dx / Math.max(1, pager.getWidth()));
                return true;
            }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                vt.computeCurrentVelocity(1000);
                float vx = vt.getXVelocity(), vy = vt.getYVelocity();
                if (mode == OPEN) settle(drawerP > 0.35f || vy < -900);
                else if (mode == CLOSE) settle(!(drawerP < 0.65f || vy > 900));
                else if (mode == PAGE) pager.settle(vx);
                else if (mode == PULL && a == MotionEvent.ACTION_UP && dy > U.dp(70)) {
                    if (topRight) IslandService.send(HomeActivity.this, "cc"); else expandShade();
                } else if (mode == NONE && a == MotionEvent.ACTION_UP && !drawerOpen) { /* tap on empty space */ }
                else if (mode == NONE && drawerOpen && a == MotionEvent.ACTION_UP) { }
                mode = NONE;
                return true;
            }
            return true;
        }
    }

    void expandShade() {
        try {
            Object sbm = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm);
        } catch (Throwable e) { }
    }

    // ------------------------------------------------------------------ lifecycle
    void updateBanner() {
        boolean ok = Settings.canDrawOverlays(this) && listenerOn();
        banner.setVisibility(ok ? View.GONE : View.VISIBLE);
    }

    boolean listenerOn() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    @Override protected void onResume() {
        super.onResume();
        hz.refresh();
        hz.boost();
        updateBanner();
        rebuild(false);
        if (Prefs.island() && Settings.canDrawOverlays(this)) IslandService.start(this);
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (Intent.ACTION_MAIN.equals(i.getAction())) {
            if (drawerOpen) settle(false); else if (pager.pos > 0.5f) pager.snap(0);
        }
    }

    @Override public void onBackPressed() { if (drawerOpen) settle(false); }

    @Override public void onConfigurationChanged(android.content.res.Configuration c) {
        super.onConfigurationChanged(c);
        drawer.setBackgroundColor(U.night(this) ? 0xD90E0F12 : 0xD9F4F4F7);
        search.setTextColor(U.text(this));
        search.setBackground(U.round(U.tile(this), 22));
        adapter.notifyDataSetChanged();
    }
}
