package dev.clean;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Very small HTTP helper. The answer always comes back on the main thread. */
final class Net {
    private Net() {}

    interface Callback { void done(boolean ok, String body); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static boolean enabled() { return Config.SERVER != null && Config.SERVER.length() > 8; }

    private static String base() {
        String s = Config.SERVER;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    static void get(String path, Callback cb) { call("GET", path, null, cb); }

    static void post(String path, String json, Callback cb) { call("POST", path, json, cb); }

    private static void call(final String method, final String path, final String body, final Callback cb) {
        if (!enabled()) { deliver(cb, false, "Сервер не указан при сборке"); return; }
        new Thread(new Runnable() {
            @Override public void run() {
                boolean ok = false;
                String out;
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(base() + path).openConnection();
                    c.setConnectTimeout(10000);
                    c.setReadTimeout(15000);
                    c.setRequestMethod(method);
                    if (body != null) {
                        c.setDoOutput(true);
                        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        OutputStream os = c.getOutputStream();
                        os.write(body.getBytes("UTF-8"));
                        os.close();
                    }
                    int code = c.getResponseCode();
                    InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                    out = in == null ? "" : read(in);
                    ok = code >= 200 && code < 300;
                } catch (Throwable t) {
                    out = String.valueOf(t.getMessage());
                }
                deliver(cb, ok, out);
            }
        }).start();
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }

    private static void deliver(final Callback cb, final boolean ok, final String body) {
        MAIN.post(new Runnable() { @Override public void run() { cb.done(ok, body); } });
    }
}
