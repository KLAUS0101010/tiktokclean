package dev.clean;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Downloads the new apk (link given by the admin) and starts the Android installer. */
final class Updater {
    private Updater() {}

    interface Progress { void text(String s); }

    private static final String ACTION = "dev.clean.INSTALL_RESULT";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void run(final Activity act, final String url, final Progress p) {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    File f = new File(act.getCacheDir(), "update.apk");
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(15000);
                    c.setReadTimeout(30000);
                    c.setInstanceFollowRedirects(true);
                    if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                    long total = c.getContentLength(), got = 0, lastShown = -1;
                    InputStream in = c.getInputStream();
                    OutputStream out = new FileOutputStream(f);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        got += n;
                        long mb = got >> 20;
                        if (mb != lastShown) {
                            lastShown = mb;
                            say(p, "Скачано " + mb + (total > 0 ? " из " + (total >> 20) : "") + " МБ");
                        }
                    }
                    out.close();
                    in.close();
                    say(p, "Скачано. Открываю установку…");
                    install(act, f);
                } catch (Throwable t) {
                    say(p, "Не получилось скачать: " + t.getMessage() + "\nОткрываю ссылку в браузере.");
                    browser(act, url);
                }
            }
        }).start();
    }

    private static void say(final Progress p, final String s) {
        MAIN.post(new Runnable() { @Override public void run() { p.text(s); } });
    }

    private static void browser(final Activity act, final String url) {
        MAIN.post(new Runnable() {
            @Override public void run() {
                try {
                    act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable ignored) { }
            }
        });
    }

    private static void install(final Activity act, final File apk) throws Exception {
        final Context ctx = act.getApplicationContext();
        final PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int id = pi.createSession(sp);
        PackageInstaller.Session s = pi.openSession(id);
        OutputStream os = s.openWrite("update", 0, apk.length());
        InputStream in = new FileInputStream(apk);
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        in.close();
        s.fsync(os);
        os.close();

        final String url = Badges.update == null ? "" : Badges.update.url;
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999);
                if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        c.startActivity(confirm);
                    }
                    return;
                }
                try { c.unregisterReceiver(this); } catch (Throwable ignored) { }
                if (st != PackageInstaller.STATUS_SUCCESS) {
                    Toast.makeText(c, "Установка не удалась (" + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) + "). Открываю ссылку.", Toast.LENGTH_LONG).show();
                    browser(act, url);
                }
            }
        };
        IntentFilter f = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, f, 4);   // 4 = RECEIVER_NOT_EXPORTED
        else ctx.registerReceiver(r, f);
        Intent intent = new Intent(ACTION).setPackage(ctx.getPackageName());
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? 0x02000000 : 0);   // 0x02000000 = FLAG_MUTABLE
        s.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).getIntentSender());
    }
}
