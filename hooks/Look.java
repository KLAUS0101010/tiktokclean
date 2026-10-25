package dev.clean;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.TextView;

/** The two looks of the mod: "Liquid Glass" for the bottom bar and the Halloween decorations. */
final class Look {
    private Look() {}

    static final String KEY_GLASS = "theme_glass";
    static final String KEY_HALLOWEEN = "theme_halloween";

    private static final int TAG_GLASS = 0x7fc1ea10;
    private static final int TAG_HALLOWEEN = 0x7fc1ea11;

    /** Names of the buttons of the bottom bar, in the languages TikTok is likely to be in. */
    private static final String[] TAB_LABELS = {
            "главная", "друзья", "входящие", "профиль", "подписки", "магазин", "обзор",
            "home", "friends", "inbox", "profile", "following", "shop", "discover", "explore", "now"};

    // ---- entry points ----------------------------------------------------------------------

    /** Put on or take off the looks for one screen, according to the menu settings. */
    static void apply(Activity a) {
        try {
            View decor = a.getWindow().getDecorView();
            if (!(decor instanceof FrameLayout)) return;
            halloween((FrameLayout) decor, Store.flag(KEY_HALLOWEEN, true));
            if (Store.flag(KEY_GLASS, true)) glass(decor);
            else unglass(decor);
        } catch (Throwable ignored) { }
    }

    /** Called after every layout of the screen (the bar can be redrawn by TikTok at any time). */
    static void pass(View decor) {
        try {
            if (Store.flag(KEY_GLASS, true)) glass(decor);
        } catch (Throwable ignored) { }
    }

    // ---- Liquid Glass ----------------------------------------------------------------------

    private static void glass(View decor) {
        View bar = findBar(decor);
        if (bar == null) return;
        Object mine = bar.getTag(TAG_GLASS);
        if (mine != null && bar.getBackground() == mine) return;       // already glass
        float d = bar.getResources().getDisplayMetrics().density;
        final float radius = 26 * d;
        // milky glass: lighter at the top edge, clear in the middle, a little milky again at the bottom
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x55FFFFFF, 0x1FFFFFFF, 0x33FFFFFF});
        g.setCornerRadius(radius);
        // the bright thin edge that makes it look like a glass plate
        g.setStroke((int) Math.max(1, 1.2f * d), 0x88FFFFFF);
        bar.setBackground(g);
        bar.setTag(TAG_GLASS, g);
        bar.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        bar.setClipToOutline(true);
    }

    private static void unglass(View decor) {
        View bar = findBar(decor);
        if (bar == null) return;
        Object mine = bar.getTag(TAG_GLASS);
        if (mine == null) return;
        bar.setTag(TAG_GLASS, null);
        if (bar.getBackground() == mine) bar.setBackground(null);
        bar.setClipToOutline(false);
    }

    /** The smallest group of views that holds at least three of the bottom bar's buttons. */
    private static View findBar(View root) {
        View[] best = new View[1];
        count(root, best);
        return best[0];
    }

    private static int count(View v, View[] best) {
        if (v instanceof TextView) {
            CharSequence s = ((TextView) v).getText();
            return s != null && isTab(s.toString()) ? 1 : 0;
        }
        if (!(v instanceof ViewGroup)) return 0;
        ViewGroup g = (ViewGroup) v;
        int total = 0;
        boolean childHas = false;
        for (int i = 0; i < g.getChildCount(); i++) {
            int c = count(g.getChildAt(i), best);
            total += c;
            if (c >= 3) childHas = true;
        }
        if (total >= 3 && !childHas && best[0] == null) best[0] = v;
        return total;
    }

    static boolean isTab(String text) {
        String s = text.trim().toLowerCase();
        for (String t : TAB_LABELS) if (s.equals(t)) return true;
        return false;
    }

    // ---- Halloween -------------------------------------------------------------------------

    private static void halloween(FrameLayout decor, boolean on) {
        Object old = decor.getTag(TAG_HALLOWEEN);
        if (on && old == null) {
            View v = new Decor(decor.getContext());
            v.setClickable(false);
            v.setFocusable(false);
            decor.addView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            decor.setTag(TAG_HALLOWEEN, v);
        } else if (!on && old instanceof View) {
            decor.removeView((View) old);
            decor.setTag(TAG_HALLOWEEN, null);
        }
    }

    /**
     * Cobwebs in the two top corners, a spider on a thread, two small pumpkins at the bottom corners.
     * Thin and half see-through, and it never takes a touch, so nothing of the app is covered or blocked.
     */
    private static final class Decor extends View {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        Decor(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE);
        }

        @Override protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            int w = getWidth(), h = getHeight();
            // webs
            c.save();
            web(c, d, 0xAAFFFFFF);
            c.translate(w, 0);
            c.scale(-1, 1);
            web(c, d, 0xAAFFFFFF);
            c.restore();
            // spider
            float sx = 74 * d, sy = 46 * d;
            line.setStrokeWidth(Math.max(1, 0.8f * d));
            line.setColor(0x88FFFFFF);
            c.drawLine(sx, 0, sx, sy, line);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xFF1B1B1F);
            c.drawCircle(sx, sy + 5 * d, 5 * d, fill);
            c.drawCircle(sx, sy - 1 * d, 3 * d, fill);
            line.setColor(0xFF1B1B1F);
            for (int i = 0; i < 4; i++) {
                float dy = (i - 1.5f) * 2.4f * d;
                c.drawLine(sx, sy + 3 * d + dy, sx - 9 * d, sy + dy - 2 * d, line);
                c.drawLine(sx, sy + 3 * d + dy, sx + 9 * d, sy + dy - 2 * d, line);
            }
            fill.setColor(0xFFFF3B30);
            c.drawCircle(sx - 1.6f * d, sy - 1.5f * d, 0.8f * d, fill);
            c.drawCircle(sx + 1.6f * d, sy - 1.5f * d, 0.8f * d, fill);
            // pumpkins
            pumpkin(c, d, 14 * d, h - 12 * d, 13 * d);
            pumpkin(c, d, w - 14 * d, h - 12 * d, 10 * d);
        }

        /** One web, drawn in the top left corner. */
        private void web(Canvas c, float d, int color) {
            float r = 66 * d;
            line.setColor(color);
            line.setStrokeWidth(Math.max(1, 0.9f * d));
            int spokes = 6;
            for (int i = 0; i < spokes; i++) {
                double a = Math.PI / 2 * i / (spokes - 1);
                c.drawLine(0, 0, (float) (r * Math.cos(a)), (float) (r * Math.sin(a)), line);
            }
            for (int ring = 1; ring <= 4; ring++) {
                float rr = r * ring / 4f;
                path.reset();
                for (int i = 0; i < spokes; i++) {
                    double a = Math.PI / 2 * i / (spokes - 1);
                    float x = (float) (rr * Math.cos(a)), y = (float) (rr * Math.sin(a));
                    if (i == 0) { path.moveTo(x, y); continue; }
                    double m = Math.PI / 2 * (i - 0.5) / (spokes - 1);
                    path.quadTo((float) (rr * 0.8 * Math.cos(m)), (float) (rr * 0.8 * Math.sin(m)), x, y);
                }
                c.drawPath(path, line);
            }
        }

        /** A pumpkin whose centre is (cx, cy) and whose half width is r. */
        private void pumpkin(Canvas c, float d, float cx, float cy, float r) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xE6FF7A00);
            c.drawOval(cx - r, cy - r * 0.78f, cx + r, cy + r * 0.78f, fill);
            fill.setColor(0xE6E86A00);
            c.drawOval(cx - r * 0.62f, cy - r * 0.8f, cx + r * 0.62f, cy + r * 0.8f, fill);
            fill.setColor(0xE6FF8C1A);
            c.drawOval(cx - r * 0.28f, cy - r * 0.82f, cx + r * 0.28f, cy + r * 0.82f, fill);
            fill.setColor(0xFF3C7A2A);
            c.drawRect(cx - r * 0.1f, cy - r * 1.05f, cx + r * 0.12f, cy - r * 0.7f, fill);
            fill.setColor(0xFF2A1400);
            path.reset();
            path.moveTo(cx - r * 0.5f, cy - r * 0.1f);
            path.lineTo(cx - r * 0.2f, cy - r * 0.1f);
            path.lineTo(cx - r * 0.35f, cy - r * 0.38f);
            path.close();
            c.drawPath(path, fill);
            path.reset();
            path.moveTo(cx + r * 0.5f, cy - r * 0.1f);
            path.lineTo(cx + r * 0.2f, cy - r * 0.1f);
            path.lineTo(cx + r * 0.35f, cy - r * 0.38f);
            path.close();
            c.drawPath(path, fill);
            path.reset();
            path.moveTo(cx - r * 0.5f, cy + r * 0.22f);
            for (int i = 1; i <= 6; i++) path.lineTo(cx - r * 0.5f + r * i / 6f, cy + r * (i % 2 == 0 ? 0.22f : 0.46f));
            path.lineTo(cx + r * 0.5f, cy + r * 0.22f);
            path.close();
            c.drawPath(path, fill);
        }
    }
}
