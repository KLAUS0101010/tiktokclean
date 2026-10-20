package dev.clean;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

/** The "note" (♪) badges: the list comes from our server, we draw the note after @username. */
final class Badges {
    private Badges() {}

    static final String NOTE = "♪";

    static final class Badge {
        final int color;
        final String reason;
        Badge(int color, String reason) { this.color = color; this.reason = reason; }
    }

    /** Update offered by the server (set by the admin). */
    static final class Update {
        final int build;
        final String url, notes;
        Update(int build, String url, String notes) { this.build = build; this.url = url; this.notes = notes; }
    }

    static volatile Map<String, Badge> map = new LinkedHashMap<String, Badge>();
    static volatile Update update;
    private static long lastRefresh;

    static void load() { try { apply(Store.getString("server_state", "")); } catch (Throwable ignored) { } }

    static void refreshIfStale() {
        if (System.currentTimeMillis() - lastRefresh > 10 * 60 * 1000L) refresh(null);
    }

    static void refresh(final Runnable after) {
        if (!Net.enabled()) return;
        lastRefresh = System.currentTimeMillis();
        Net.get("/badges", new Net.Callback() {
            @Override public void done(boolean ok, String body) {
                if (ok && apply(body)) {
                    Store.putString("server_state", body);
                    Entry.redecorate();
                }
                if (after != null) after.run();
            }
        });
    }

    /** Reads the server answer ({"badges":{...},"update":{...}}). Returns false if it is garbage. */
    static boolean apply(String body) {
        if (body == null || body.length() < 2) return false;
        try {
            Map<String, Object> root = Json.obj(Json.parse(body));
            Map<String, Object> b = Json.obj(root.get("badges"));
            Map<String, Badge> m = new LinkedHashMap<String, Badge>();
            for (Map.Entry<String, Object> e : b.entrySet()) {
                Map<String, Object> v = Json.obj(e.getValue());
                int col;
                try { col = Color.parseColor(Json.str(v.get("c"), "#FFB300")); } catch (Throwable t) { col = 0xFFFFB300; }
                m.put(e.getKey().toLowerCase(), new Badge(col, Json.str(v.get("r"), "")));
            }
            map = m;
            Map<String, Object> u = Json.obj(root.get("update"));
            String url = Json.str(u.get("url"), "");
            update = url.startsWith("https://") ? new Update((int) Json.num(u.get("build"), 0), url, Json.str(u.get("notes"), "")) : null;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- drawing the note --------------------------------------------------------------

    static void decorate(View v) {
        if (map.isEmpty()) return;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) decorate(g.getChildAt(i));
        } else if (v instanceof TextView && !(v instanceof EditText)) {
            decorateText((TextView) v);
        }
    }

    private static void decorateText(final TextView t) {
        CharSequence cs = t.getText();
        if (cs == null) return;
        int n = cs.length();
        if (n < 3 || n > 40 || cs.charAt(0) != '@') return;
        if (cs.charAt(n - 1) == NOTE.charAt(0)) return;            // already has our note
        final String user = cs.toString().substring(1).trim().toLowerCase();
        final Badge b = map.get(user);
        if (b == null) return;
        SpannableStringBuilder ssb = new SpannableStringBuilder(cs);
        ssb.append(' ');
        int st = ssb.length();
        ssb.append(NOTE);
        ssb.setSpan(new ForegroundColorSpan(b.color), st, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new ClickableSpan() {
            @Override public void onClick(View w) { Menu.showBadge(activityOf(w), user, b); }
            @Override public void updateDrawState(TextPaint ds) { ds.setColor(b.color); ds.setUnderlineText(false); ds.setFakeBoldText(true); }
        }, st, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        t.setText(ssb);
        t.setMovementMethod(LinkMovementMethod.getInstance());
    }

    static Activity activityOf(View v) {
        Context c = v.getContext();
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return Entry.current;
    }
}
