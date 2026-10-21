package dev.clean;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Fetches the video page, asks for the file sizes and saves the chosen file to the gallery. */
final class Download {
    private Download() {}

    interface Info { void done(VideoInfo info); }
    interface Size { void done(long bytes); }
    interface Progress { void text(String s); void finished(boolean ok, String message); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";

    /** Called after the app puts something on the clipboard: if it is a TikTok link, offer to download it. */
    static void offer(ClipData clip) {
        try {
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence cs = clip.getItemAt(0).getText();
            String link = VideoInfo.findLink(cs == null ? null : cs.toString());
            if (link != null) Entry.showChip(link);
        } catch (Throwable ignored) { }
    }

    private static HttpURLConnection open(String url, String method, int redirects) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Referer", "https://www.tiktok.com/");
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        int code = c.getResponseCode();
        if (code >= 300 && code < 400 && redirects > 0) {
            String loc = c.getHeaderField("Location");
            c.disconnect();
            if (loc != null) return open(new URL(new URL(url), loc).toString(), method, redirects - 1);
        }
        return c;
    }

    static void fetch(final String link, final Info cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                VideoInfo info;
                try {
                    HttpURLConnection c = open(link, "GET", 6);
                    if (c.getResponseCode() != 200) {
                        info = new VideoInfo();
                        info.error = "TikTok ответил кодом " + c.getResponseCode() + ".";
                    } else {
                        InputStream in = c.getInputStream();
                        StringBuilder sb = new StringBuilder();
                        byte[] buf = new byte[32768];
                        int n;
                        while ((n = in.read(buf)) > 0 && sb.length() < 6000000) sb.append(new String(buf, 0, n, "UTF-8"));
                        in.close();
                        info = VideoInfo.parse(sb.toString());
                        if (info.id.isEmpty()) info.id = VideoInfo.videoId(c.getURL().toString());
                    }
                } catch (Throwable t) {
                    info = new VideoInfo();
                    info.error = "Нет связи с TikTok: " + t.getMessage();
                }
                final VideoInfo r = info;
                MAIN.post(new Runnable() { @Override public void run() { cb.done(r); } });
            }
        }).start();
    }

    /** Exact size of the file (asks the server, nothing is downloaded). 0 if it does not tell. */
    static void exactSize(final String url, final Size cb) {
        new Thread(new Runnable() {
            @Override public void run() {
                long len = 0;
                try {
                    HttpURLConnection c = open(url, "HEAD", 4);
                    if (c.getResponseCode() == 200) len = c.getContentLengthLong();
                    c.disconnect();
                } catch (Throwable ignored) { }
                final long out = Math.max(len, 0);
                MAIN.post(new Runnable() { @Override public void run() { cb.done(out); } });
            }
        }).start();
    }

    static void save(final Activity act, final VideoInfo.Quality q, final String id, final Progress p) {
        new Thread(new Runnable() {
            @Override public void run() {
                String msg;
                boolean ok = false;
                try {
                    HttpURLConnection c = open(q.url, "GET", 4);
                    if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                    long total = c.getContentLengthLong(), got = 0, last = -1;
                    String name = "TikTok_" + (id == null || id.isEmpty() ? String.valueOf(System.currentTimeMillis()) : id) + ".mp4";
                    Target t = target(act, name, "video/mp4", "Movies/TikTok Clean", true);
                    InputStream in = c.getInputStream();
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        t.out.write(buf, 0, n);
                        got += n;
                        long mb = got >> 20;
                        if (mb != last) {
                            last = mb;
                            post(p, "Скачано " + mb + (total > 0 ? " из " + (total >> 20) : "") + " МБ");
                        }
                    }
                    in.close();
                    t.out.close();
                    t.finish();
                    ok = true;
                    msg = "Готово. Видео без водяного знака лежит в галерее: " + t.where;
                } catch (Throwable e) {
                    msg = "Не получилось скачать: " + e.getMessage();
                }
                final boolean fok = ok;
                final String fmsg = msg;
                MAIN.post(new Runnable() { @Override public void run() { p.finished(fok, fmsg); } });
            }
        }).start();
    }

    /** Saves every picture of a photo post to the gallery (no sound). */
    static void saveImages(final Activity act, final java.util.List<String> urls, final String id, final Progress p) {
        new Thread(new Runnable() {
            @Override public void run() {
                int done = 0;
                String msg;
                boolean ok = false;
                try {
                    String base = "TikTok_" + (id == null || id.isEmpty() ? String.valueOf(System.currentTimeMillis()) : id);
                    for (int i = 0; i < urls.size(); i++) {
                        post(p, "Скачиваю фото " + (i + 1) + " из " + urls.size() + "…");
                        HttpURLConnection c = open(urls.get(i), "GET", 4);
                        if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                        String ct = String.valueOf(c.getContentType()).toLowerCase();
                        String ext = ct.contains("webp") ? "webp" : ct.contains("png") ? "png" : ct.contains("heic") ? "heic" : "jpg";
                        String mime = ct.startsWith("image/") ? ct.split(";")[0].trim() : "image/jpeg";
                        Target t = target(act, base + "_" + (i + 1) + "." + ext, mime, "Pictures/TikTok Clean", false);
                        InputStream in = c.getInputStream();
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) t.out.write(buf, 0, n);
                        in.close();
                        t.out.close();
                        t.finish();
                        done++;
                    }
                    ok = true;
                    msg = "Готово: сохранено фото " + done + ". Они в галерее, папка Pictures/TikTok Clean. Звук не сохраняется.";
                } catch (Throwable e) {
                    msg = "Сохранено фото: " + done + ". Ошибка: " + e.getMessage();
                }
                final boolean fok = ok;
                final String fmsg = msg;
                MAIN.post(new Runnable() { @Override public void run() { p.finished(fok, fmsg); } });
            }
        }).start();
    }

    private static void post(final Progress p, final String s) {
        MAIN.post(new Runnable() { @Override public void run() { p.text(s); } });
    }

    private static final class Target {
        OutputStream out;
        String where;
        Runnable done;
        void finish() { if (done != null) done.run(); }
    }

    private static Target target(Activity act, String name, String mime, String folder, boolean video) throws Exception {
        final Target t = new Target();
        if (Build.VERSION.SDK_INT >= 29) {
            final ContentResolver cr = act.getContentResolver();
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, folder);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            final Uri uri = cr.insert(video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new Exception("галерея не дала создать файл");
            t.out = cr.openOutputStream(uri);
            t.where = folder;
            t.done = new Runnable() {
                @Override public void run() {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    cr.update(uri, v, null, null);
                }
            };
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES), "TikTok Clean");
            dir.mkdirs();
            File f = new File(dir, name);
            t.out = new FileOutputStream(f);
            t.where = f.getParent();
        }
        return t;
    }
}
