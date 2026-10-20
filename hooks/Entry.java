package dev.clean;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Starts everything the mod does inside the app: the settings button, the notes, the update hint. */
final class Entry {
    private Entry() {}

    private static final int TAG_DECOR = 0x7fc1ea01;
    private static final int TAG_BAR = 0x7fc1ea02;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static volatile Activity current;
    private static boolean started, updateToastShown;

    static void start() {
        if (started) return;
        Application app = Store.app();
        if (app == null) return;
        started = true;
        try {
            Badges.load();
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override public void onActivityResumed(Activity a) { resumed(a); }
                @Override public void onActivityPaused(Activity a) { if (current == a) current = null; }
                @Override public void onActivityCreated(Activity a, Bundle b) { }
                @Override public void onActivityStarted(Activity a) { }
                @Override public void onActivityStopped(Activity a) { }
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
                @Override public void onActivityDestroyed(Activity a) { }
            });
            Badges.refresh(null);
        } catch (Throwable ignored) { }
    }

    private static void resumed(final Activity a) {
        current = a;
        try {
            if (isSettings(a)) addBar(a);
            watchLayout(a);
            Badges.refreshIfStale();
            Badges.Update u = Badges.update;
            if (!updateToastShown && u != null && u.build > Config.BUILD) {
                updateToastShown = true;
                Toast.makeText(a, "TikTok Clean: есть обновление. Настройки → Меню мода → Автообновление", Toast.LENGTH_LONG).show();
            }
        } catch (Throwable ignored) { }
    }

    private static boolean isSettings(Activity a) {
        return a.getClass().getName().toLowerCase().contains("setting");
    }

    /** The round "Меню мода" button at the bottom of every settings screen. */
    private static void addBar(final Activity a) {
        View decor = a.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout) || decor.getTag(TAG_BAR) != null) return;
        decor.setTag(TAG_BAR, Boolean.TRUE);
        float d = a.getResources().getDisplayMetrics().density;
        TextView t = new TextView(a);
        t.setText(Badges.NOTE + "  Меню мода — Clean");
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding((int) (22 * d), (int) (13 * d), (int) (22 * d), (int) (13 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2FE2C55);
        bg.setCornerRadius(40 * d);
        t.setBackground(bg);
        t.setElevation(12 * d);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = (int) (88 * d);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { Menu.open(a); }
        });
        ((FrameLayout) decor).addView(t, lp);
    }

    /** Re-draws the notes whenever the screen changes. */
    private static void watchLayout(final Activity a) {
        final View decor = a.getWindow().getDecorView();
        if (decor.getTag(TAG_DECOR) != null) return;
        decor.setTag(TAG_DECOR, Boolean.TRUE);
        final boolean[] pending = {false};
        final Runnable job = new Runnable() {
            @Override public void run() {
                pending[0] = false;
                try { Badges.decorate(decor); } catch (Throwable ignored) { }
            }
        };
        decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (pending[0]) return;
                pending[0] = true;
                MAIN.postDelayed(job, 250);
            }
        });
    }

    static void redecorate() {
        Activity a = current;
        if (a == null) return;
        try { Badges.decorate(a.getWindow().getDecorView()); } catch (Throwable ignored) { }
    }
}
