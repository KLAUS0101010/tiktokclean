package dev.clean;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

/** Settings of the mod, kept in the app's own private storage. */
final class Store {
    private Store() {}

    private static volatile Region region;

    static Application app() {
        try {
            Object o = Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            return o instanceof Application ? (Application) o : null;
        } catch (Throwable t) { return null; }
    }

    private static SharedPreferences sp() {
        Context c = app();
        return c == null ? null : c.getSharedPreferences("clean_mod", Context.MODE_PRIVATE);
    }

    /** The country the phone pretends to be in right now. */
    static Region region() {
        Region r = region;
        if (r != null) return r;
        SharedPreferences p = sp();
        if (p == null) return Region.byCode(Config.REGION);   // too early, app not ready yet; do not cache
        r = Region.byCode(p.getString("region", Config.REGION));
        region = r;
        return r;
    }

    static void setRegion(String code) {
        Region r = Region.byCode(code);
        region = r;
        SharedPreferences p = sp();
        if (p != null) p.edit().putString("region", r.code).apply();
    }

    static String getString(String key, String def) {
        SharedPreferences p = sp();
        return p == null ? def : p.getString(key, def);
    }

    static void putString(String key, String v) {
        SharedPreferences p = sp();
        if (p != null) p.edit().putString(key, v).apply();
    }

    /** A yes/no setting of the mod menu. */
    static boolean flag(String key, boolean def) {
        SharedPreferences p = sp();
        return p == null ? def : p.getBoolean(key, def);
    }

    static void setFlag(String key, boolean v) {
        SharedPreferences p = sp();
        if (p != null) p.edit().putBoolean(key, v).apply();
    }

    static int number(String key, int def) {
        SharedPreferences p = sp();
        return p == null ? def : p.getInt(key, def);
    }

    static void setNumber(String key, int v) {
        SharedPreferences p = sp();
        if (p != null) p.edit().putInt(key, v).apply();
    }
}
