package com.robinui.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeActivity extends Activity {
    GestureLayout root;
    PagerView pager;
    LinearLayout dots, dock;
    FrameLayout search;
    EditText query;
    ListView results;
    TextView clock, date;
    PopupWindow popup;
    List<Apps.App> apps = new ArrayList<>(), shown = new ArrayList<>();
    int insetTop, insetBottom, builtKey;
    boolean searchOpen;
    final Handler h = new Handler(Looper.getMainLooper());
    final Runnable unboost = new Runnable() { public void run() { Hz.window(getWindow(), false); } };
    static final int ICON = 54;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(0);
        getWindow().setNavigationBarColor(0);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        root = new GestureLayout(this);
        setContentView(root);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets i) {
                insetTop = i.getSystemWindowInsetTop();
                insetBottom = i.getSystemWindowInsetBottom();
                layoutParts();
                return i;
            }
        });

        pager = new PagerView(this);
        root.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        root.addView(dots, new FrameLayout.LayoutParams(-1, U.dp(this, 14), Gravity.BOTTOM));
        dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(U.rr(0x66202024, U.dp(this, 30)));
        root.addView(dock, new FrameLayout.LayoutParams(-1, U.dp(this, 80), Gravity.BOTTOM));
        pager.setListener(new PagerView.Listener() { public void onPage(int p, int n) { updateDots(p, n); } });
        buildSearch();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        registerReceiver(pkgRx, pf);
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeRx, tf);

        reload();
        if (!Prefs.b(this, "setup_shown", false)) {
            Prefs.b(this, "setup_shown", true);
            startActivity(new Intent(this, SettingsActivity.class));
        }
    }

    final BroadcastReceiver pkgRx = new BroadcastReceiver() { public void onReceive(Context c, Intent i) { reload(); } };
    final BroadcastReceiver timeRx = new BroadcastReceiver() { public void onReceive(Context c, Intent i) { tick(); } };

    @Override protected void onResume() {
        super.onResume();
        IslandService.ensure(this);
        tick();
        Hz.window(getWindow(), true);
        h.postDelayed(unboost, 1500);
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (popup != null) popup.dismiss();
        if (searchOpen) closeSearch();
        pager.goTo(0, true);
    }

    @Override protected void onDestroy() {
        try { unregisterReceiver(pkgRx); unregisterReceiver(timeRx); } catch (Throwable t) { }
        super.onDestroy();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) { h.removeCallbacks(unboost); Hz.window(getWindow(), true); }
        else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) { h.removeCallbacks(unboost); h.postDelayed(unboost, 1500); }
        return super.dispatchTouchEvent(e);
    }

    @Override public void onBackPressed() {
        if (popup != null && popup.isShowing()) popup.dismiss();
        else if (searchOpen) closeSearch();
        else pager.goTo(0, true);
    }

    // ---------- data ----------
    void reload() {
        apps = Apps.load(this);
        builtKey = 0;
        layoutParts();
        filter(query == null ? "" : query.getText().toString());
    }

    void tick() {
        if (clock == null) return;
        boolean h24 = android.text.format.DateFormat.is24HourFormat(this);
        clock.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", Locale.getDefault()).format(new Date()));
        date.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()));
    }

    List<Apps.App> dockApps() {
        String s = Prefs.s(this, "dock", "");
        if (s.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            Intent[] defs = {
                    new Intent(Intent.ACTION_DIAL),
                    new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
                    new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
                    new Intent(MediaStore.ACTION_IMAGE_CAPTURE)};
            for (Intent in : defs) {
                ResolveInfo r = getPackageManager().resolveActivity(in, 0);
                if (r == null || r.activityInfo == null) continue;
                for (Apps.App a : apps) {
                    if (a.pkg.equals(r.activityInfo.packageName)) { if (sb.indexOf(a.key()) < 0) sb.append(a.key()).append(','); break; }
                }
            }
            s = sb.toString();
            Prefs.s(this, "dock", s);
        }
        List<Apps.App> out = new ArrayList<>();
        for (String k : s.split(",")) {
            if (k.isEmpty()) continue;
            for (Apps.App a : apps) if (a.key().equals(k)) { out.add(a); break; }
        }
        return out;
    }

    // ---------- layout ----------
    void layoutParts() {
        if (root == null || root.getHeight() == 0) return;
        int dockH = U.dp(this, 80);
        FrameLayout.LayoutParams dl = (FrameLayout.LayoutParams) dock.getLayoutParams();
        dl.leftMargin = dl.rightMargin = U.dp(this, 14);
        dl.bottomMargin = insetBottom + U.dp(this, 10);
        dock.setLayoutParams(dl);
        FrameLayout.LayoutParams ds = (FrameLayout.LayoutParams) dots.getLayoutParams();
        ds.bottomMargin = insetBottom + U.dp(this, 10) + dockH + U.dp(this, 10);
        dots.setLayoutParams(ds);
        FrameLayout.LayoutParams pl = (FrameLayout.LayoutParams) pager.getLayoutParams();
        pl.topMargin = insetTop + U.dp(this, 8);
        pl.bottomMargin = insetBottom + U.dp(this, 10) + dockH + U.dp(this, 32);
        pager.setLayoutParams(pl);
        int pagerH = root.getHeight() - pl.topMargin - pl.bottomMargin;
        int rowH = U.dp(this, 96);
        int cols = Prefs.i(this, "cols", 4);
        int rows = Math.max(3, pagerH / rowH);
        int key = rows * 100 + cols * 10000 + apps.size() * 1000000 + pagerH;
        if (key == builtKey) return;
        builtKey = key;
        buildPages(rows, cols, rowH);
        buildDock();
    }

    void buildPages(int rows, int cols, int rowH) {
        List<View> pages = new ArrayList<>();
        boolean labels = !Prefs.b(this, "hide_labels", false);
        int i = 0, pageNo = 0;
        do {
            LinearLayout page = new LinearLayout(this);
            page.setOrientation(LinearLayout.VERTICAL);
            page.setPadding(U.dp(this, 10), 0, U.dp(this, 10), 0);
            int r = rows;
            if (pageNo == 0) {
                LinearLayout head = new LinearLayout(this);
                head.setOrientation(LinearLayout.VERTICAL);
                head.setPadding(U.dp(this, 14), U.dp(this, 14), 0, 0);
                clock = new TextView(this);
                clock.setTextColor(Color.WHITE);
                clock.setTextSize(58);
                clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                clock.setShadowLayer(8, 0, 2, 0x66000000);
                date = new TextView(this);
                date.setTextColor(0xE6FFFFFF);
                date.setTextSize(15);
                date.setShadowLayer(6, 0, 1, 0x66000000);
                head.addView(clock);
                head.addView(date);
                page.addView(head, new LinearLayout.LayoutParams(-1, rowH * 2));
                r = Math.max(1, rows - 2);
                tick();
            }
            GridLayout g = new GridLayout(this);
            g.setColumnCount(cols);
            g.setRowCount(r);
            for (int k = 0; k < r * cols && i < apps.size(); k++, i++) {
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(k / cols, 1f), GridLayout.spec(k % cols, 1f));
                lp.width = 0;
                lp.height = rowH;
                g.addView(cell(apps.get(i), labels), lp);
            }
            page.addView(g, new LinearLayout.LayoutParams(-1, -2));
            pages.add(page);
            pageNo++;
        } while (i < apps.size());
        pager.setPages(pages);
        // pager wraps pages at its own width; make sure sizes are applied
        pager.requestLayout();
    }

    void buildDock() {
        dock.removeAllViews();
        for (Apps.App a : dockApps()) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
            dock.addView(cell(a, false), lp);
        }
    }

    View cell(final Apps.App a, boolean label) {
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        final ImageView iv = new ImageView(this);
        int s = U.dp(this, ICON);
        c.addView(iv, new LinearLayout.LayoutParams(s, s));
        if (label) {
            TextView t = new TextView(this);
            t.setText(a.label);
            t.setTextColor(Color.WHITE);
            t.setTextSize(11);
            t.setSingleLine();
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setShadowLayer(6, 0, 1, 0x88000000);
            t.setPadding(U.dp(this, 2), U.dp(this, 5), U.dp(this, 2), 0);
            c.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        Apps.icon(this, a, s, new Apps.Cb() { public void on(android.graphics.Bitmap b) { iv.setImageBitmap(b); } });
        c.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                int ac = e.getActionMasked();
                if (ac == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).setInterpolator(new DecelerateInterpolator()).start();
                else if (ac == MotionEvent.ACTION_UP || ac == MotionEvent.ACTION_CANCEL) v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(2.2f)).start();
                return false;
            }
        });
        c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { launch(a, v); } });
        c.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) { menu(a, v); return true; }
        });
        return c;
    }

    void launch(Apps.App a, View v) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(a.cn())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i, ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight()).toBundle());
        } catch (Throwable t) { }
    }

    void updateDots(int p, int n) {
        dots.removeAllViews();
        if (n <= 1) return;
        for (int i = 0; i < n; i++) {
            View d = new View(this);
            boolean on = i == p;
            d.setBackground(U.rr(on ? Color.WHITE : 0x88FFFFFF, U.dp(this, 3)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.dp(this, on ? 16 : 6), U.dp(this, 6));
            lp.setMargins(U.dp(this, 3), 0, U.dp(this, 3), 0);
            dots.addView(d, lp);
        }
    }

    // ---------- long-press menu ----------
    void menu(final Apps.App a, View anchor) {
        if (popup != null) popup.dismiss();
        final boolean inDock = Prefs.s(this, "dock", "").contains(a.key());
        LinearLayout m = new LinearLayout(this);
        m.setOrientation(LinearLayout.VERTICAL);
        m.setBackground(U.rr(0xF0222226, U.dp(this, 20)));
        m.setPadding(0, U.dp(this, 6), 0, U.dp(this, 6));
        String[] names = {"App info", "Uninstall", "Hide app", inDock ? "Remove from dock" : "Add to dock"};
        for (int idx = 0; idx < names.length; idx++) {
            final int f = idx;
            TextView t = new TextView(this);
            t.setText(names[idx]);
            t.setTextColor(idx == 1 ? 0xFFFF6B6B : Color.WHITE);
            t.setTextSize(15);
            t.setPadding(U.dp(this, 20), U.dp(this, 12), U.dp(this, 28), U.dp(this, 12));
            t.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { popup.dismiss(); act(f, a, inDock); }
            });
            m.addView(t);
        }
        m.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup = new PopupWindow(m, -2, -2, true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        popup.setElevation(U.dp(this, 12));
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int sw = root.getWidth();
        int x = Math.max(U.dp(this, 10), Math.min(loc[0] + anchor.getWidth() / 2 - m.getMeasuredWidth() / 2, sw - m.getMeasuredWidth() - U.dp(this, 10)));
        int y = loc[1] + anchor.getHeight();
        if (y + m.getMeasuredHeight() > root.getHeight() - insetBottom) y = loc[1] - m.getMeasuredHeight();
        popup.showAtLocation(root, Gravity.TOP | Gravity.START, x, y);
        m.setScaleX(0.8f); m.setScaleY(0.8f); m.setAlpha(0f);
        m.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).setInterpolator(new OvershootInterpolator(1.4f)).start();
        anchor.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }

    void act(int what, Apps.App a, boolean inDock) {
        try {
            if (what == 0) startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
            else if (what == 1) startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)));
            else if (what == 2) { Set<String> hd = Prefs.set(this, "hidden"); hd.add(a.key()); Prefs.set(this, "hidden", hd); reload(); }
            else {
                String d = Prefs.s(this, "dock", "");
                if (inDock) d = d.replace(a.key() + ",", ""); else d = d + a.key() + ",";
                Prefs.s(this, "dock", d);
                builtKey = 0;
                layoutParts();
            }
        } catch (Throwable t) { }
    }

    // ---------- search / all apps ----------
    void buildSearch() {
        search = new FrameLayout(this);
        search.setBackgroundColor(0xF2101014);
        search.setVisibility(View.GONE);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(U.dp(this, 16), 0, U.dp(this, 16), 0);
        search.addView(col, new FrameLayout.LayoutParams(-1, -1));
        View handle = new View(this);
        handle.setBackground(U.rr(0x66FFFFFF, U.dp(this, 3)));
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(U.dp(this, 40), U.dp(this, 5));
        hl.gravity = Gravity.CENTER_HORIZONTAL;
        hl.topMargin = U.dp(this, 10);
        hl.bottomMargin = U.dp(this, 10);
        col.addView(handle, hl);
        query = new EditText(this);
        query.setHint("Search apps");
        query.setHintTextColor(0x88FFFFFF);
        query.setTextColor(Color.WHITE);
        query.setSingleLine();
        query.setImeOptions(EditorInfo.IME_ACTION_GO);
        query.setBackground(U.rr(U.TILE, U.dp(this, 26)));
        query.setPadding(U.dp(this, 20), U.dp(this, 12), U.dp(this, 20), U.dp(this, 12));
        col.addView(query, new LinearLayout.LayoutParams(-1, -2));
        results = new ListView(this);
        results.setDivider(null);
        results.setVerticalScrollBarEnabled(false);
        col.addView(results, new LinearLayout.LayoutParams(-1, 0, 1f));
        results.setAdapter(adapter);
        results.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) { launch(shown.get(pos), v); closeSearch(); }
        });
        results.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(android.widget.AdapterView<?> p, View v, int pos, long id) { menu(shown.get(pos), v); return true; }
        });
        query.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { filter(s.toString()); }
            public void afterTextChanged(Editable s) { }
        });
        query.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int id, KeyEvent e) {
                if (!shown.isEmpty()) { launch(shown.get(0), results); closeSearch(); }
                return true;
            }
        });
        handle.setOnTouchListener(new View.OnTouchListener() {
            float y0;
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) y0 = e.getRawY();
                else if (e.getAction() == MotionEvent.ACTION_UP && e.getRawY() - y0 > U.dp(HomeActivity.this, 40)) closeSearch();
                return true;
            }
        });
        root.addView(search, new FrameLayout.LayoutParams(-1, -1));
    }

    void filter(String q) {
        shown.clear();
        String s = q.trim().toLowerCase(Locale.getDefault());
        for (Apps.App a : apps) if (s.isEmpty() || a.label.toLowerCase(Locale.getDefault()).contains(s)) shown.add(a);
        adapter.notifyDataSetChanged();
    }

    final BaseAdapter adapter = new BaseAdapter() {
        public int getCount() { return shown.size(); }
        public Object getItem(int i) { return shown.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View cv, android.view.ViewGroup p) {
            LinearLayout row;
            ImageView iv;
            TextView tv;
            if (cv == null) {
                row = new LinearLayout(HomeActivity.this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(U.dp(HomeActivity.this, 6), U.dp(HomeActivity.this, 8), 0, U.dp(HomeActivity.this, 8));
                iv = new ImageView(HomeActivity.this);
                int s = U.dp(HomeActivity.this, 44);
                row.addView(iv, new LinearLayout.LayoutParams(s, s));
                tv = new TextView(HomeActivity.this);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(16);
                tv.setPadding(U.dp(HomeActivity.this, 16), 0, 0, 0);
                row.addView(tv);
                row.setTag(new Object[]{iv, tv});
            } else {
                row = (LinearLayout) cv;
                Object[] t = (Object[]) row.getTag();
                iv = (ImageView) t[0];
                tv = (TextView) t[1];
            }
            Apps.App a = shown.get(i);
            tv.setText(a.label);
            iv.setImageBitmap(null);
            final ImageView fiv = iv;
            Apps.icon(HomeActivity.this, a, U.dp(HomeActivity.this, 44), new Apps.Cb() { public void on(android.graphics.Bitmap b) { fiv.setImageBitmap(b); } });
            return row;
        }
    };

    void openSearch() {
        if (searchOpen) return;
        searchOpen = true;
        search.setPadding(0, insetTop, 0, insetBottom);
        search.setVisibility(View.VISIBLE);
        search.setTranslationY(root.getHeight());
        search.animate().translationY(0).setDuration(300).setInterpolator(new DecelerateInterpolator(1.8f)).withEndAction(new Runnable() {
            public void run() {
                query.requestFocus();
                InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                im.showSoftInput(query, 0);
            }
        }).start();
        pager.animate().alpha(0.4f).setDuration(250).start();
    }

    void closeSearch() {
        if (!searchOpen) return;
        searchOpen = false;
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        im.hideSoftInputFromWindow(query.getWindowToken(), 0);
        query.setText("");
        search.animate().translationY(root.getHeight()).setDuration(240).setInterpolator(new DecelerateInterpolator(1.5f)).withEndAction(new Runnable() {
            public void run() { search.setVisibility(View.GONE); }
        }).start();
        pager.animate().alpha(1f).setDuration(240).start();
    }

    // ---------- gestures ----------
    void notificationShade() {
        try {
            Object sb = getSystemService("statusbar");
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Throwable t) { }
    }

    class GestureLayout extends FrameLayout {
        float x0, y0;
        boolean swiping;
        final int slop = ViewConfiguration.get(HomeActivity.this).getScaledTouchSlop();

        GestureLayout(Context c) { super(c); }

        @Override public boolean onInterceptTouchEvent(MotionEvent e) {
            if (searchOpen) return false;
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { x0 = e.getX(); y0 = e.getY(); swiping = false; }
            else if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dy = e.getY() - y0, dx = e.getX() - x0;
                if (Math.abs(dy) > slop * 1.5f && Math.abs(dy) > Math.abs(dx) * 1.6f) { swiping = true; return true; }
            }
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!swiping) return true;
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                float dy = e.getY() - y0;
                boolean topArea = y0 < getHeight() * 0.2f;
                if (dy < -U.dp(HomeActivity.this, 70)) openSearch();
                else if (dy > U.dp(HomeActivity.this, 70)) {
                    if (topArea && x0 > getWidth() * 0.5f) ControlCenter.toggle(HomeActivity.this);
                    else if (topArea) notificationShade();
                    else openSearch();
                }
                swiping = false;
            }
            return true;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            layoutParts();
        }
    }
}
