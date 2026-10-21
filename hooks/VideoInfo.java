package dev.clean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the data of a TikTok video page and lists the qualities that can be downloaded (no Android classes). */
public final class VideoInfo {
    public static final class Quality {
        public String label;      // "1080p · H.265"
        public String url;
        public long bytes;        // 0 = unknown
        public boolean exact;     // true when the size is the real file size
        public long bitrate;      // bits per second, 0 = unknown
    }

    public final List<Quality> qualities = new ArrayList<Quality>();
    public final List<String> images = new ArrayList<String>();   // photo posts: one link per picture
    public String id = "";
    public long durationSec;
    public String error;          // null when everything is fine

    private static final Pattern LINK = Pattern.compile("https?://(?:[a-z0-9-]+\\.)?tiktok\\.com/[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID = Pattern.compile("/video/(\\d{8,25})");

    /** First TikTok link inside a text, or null. */
    public static String findLink(String text) {
        if (text == null) return null;
        Matcher m = LINK.matcher(text);
        if (!m.find()) return null;
        String u = m.group();
        while (!u.isEmpty() && ".,;:!?)]}".indexOf(u.charAt(u.length() - 1)) >= 0) u = u.substring(0, u.length() - 1);
        return u;
    }

    public static String videoId(String url) {
        if (url == null) return "";
        Matcher m = ID.matcher(url);
        return m.find() ? m.group(1) : "";
    }

    /** Pulls the JSON out of the page and builds the list of qualities. Never throws. */
    public static VideoInfo parse(String html) {
        VideoInfo info = new VideoInfo();
        try {
            String json = extractJson(html);
            if (json == null) { info.error = "На странице нет данных о видео (возможно, TikTok запросил проверку или видео закрыто)."; return info; }
            Object root = Json.parse(json);
            Object idv0 = findKey(root, "itemStruct");
            if (idv0 instanceof Map) info.id = Json.str(((Map<?, ?>) idv0).get("id"), "");
            Map<String, Object> post = findMapWith(root, "imagePost");
            Object pimgs = post == null ? null : Json.obj(post.get("imagePost")).get("images");
            if (pimgs instanceof List) {
                for (Object o : (List<?>) pimgs) {
                    String u = firstUrl(Json.obj(Json.obj(o).get("imageURL")).get("urlList"));
                    if (u != null && !info.images.contains(u)) info.images.add(u);
                }
                if (!info.images.isEmpty()) return info;      // a photo post: pictures only, no sound
            }
            Map<String, Object> video = findMapWith(root, "playAddr");
            if (video == null) video = findMapWith(root, "bitrateInfo");
            if (video == null) { info.error = "Видео не найдено в данных страницы (закрытое или удалённое?)."; return info; }
            info.durationSec = toLong(video.get("duration"));
            Object idv = findKey(root, "itemStruct");
            if (idv instanceof Map) info.id = Json.str(((Map<?, ?>) idv).get("id"), "");

            Set<String> seen = new HashSet<String>();
            Object br = video.get("bitrateInfo");
            if (br instanceof List) {
                for (Object o : (List<?>) br) {
                    Map<String, Object> m = Json.obj(o);
                    Map<String, Object> addr = Json.obj(m.get("PlayAddr"));
                    String url = firstUrl(addr.get("UrlList"));
                    if (url == null || !seen.add(url)) continue;
                    Quality q = new Quality();
                    q.url = url;
                    q.bitrate = toLong(m.get("Bitrate"));
                    q.bytes = toLong(addr.get("DataSize"));
                    q.exact = q.bytes > 0;
                    int w = (int) toLong(addr.get("Width")), h = (int) toLong(addr.get("Height"));
                    q.label = label(w, h, Json.str(m.get("CodecType"), ""), Json.str(m.get("GearName"), ""), q.bitrate);
                    info.qualities.add(q);
                }
            }
            String plain = video.get("playAddr") instanceof String ? (String) video.get("playAddr") : null;
            if (plain != null && plain.startsWith("http") && seen.add(plain)) {
                Quality q = new Quality();
                q.url = plain;
                q.bytes = toLong(video.get("size"));
                q.exact = q.bytes > 0;
                q.label = label((int) toLong(video.get("width")), (int) toLong(video.get("height")), "", "стандартное", 0);
                info.qualities.add(q);
            }
            for (Quality q : info.qualities) {
                if (q.bytes <= 0 && q.bitrate > 0 && info.durationSec > 0) q.bytes = q.bitrate * info.durationSec / 8;   // estimate
            }
            final long dur = Math.max(info.durationSec, 1);
            Collections.sort(info.qualities, new Comparator<Quality>() {
                @Override public int compare(Quality a, Quality b) {
                    long x = a.bitrate > 0 ? a.bitrate : a.bytes * 8 / dur, y = b.bitrate > 0 ? b.bitrate : b.bytes * 8 / dur;
                    return x == y ? 0 : (x < y ? 1 : -1);
                }
            });
            if (info.qualities.isEmpty()) info.error = "У видео не нашлось ссылок на файл.";
        } catch (Throwable t) {
            info.error = "Не удалось разобрать страницу: " + t;
        }
        return info;
    }

    static String label(int w, int h, String codec, String gear, long bitrate) {
        StringBuilder b = new StringBuilder();
        int side = Math.min(w, h);
        if (side > 0) b.append(side).append('p');
        else if (!gear.isEmpty()) b.append(gear);
        else b.append("видео");
        String c = codec.toLowerCase();
        if (c.contains("bytevc1") || c.contains("265") || c.contains("hevc")) b.append(" · H.265");
        else if (c.contains("264") || c.equals("h264")) b.append(" · H.264");
        if (bitrate > 0) b.append(" · ").append(bitrate / 1000).append(" кбит/с");
        return b.toString();
    }

    /** "12.4 МБ", with "~" in front when it is only an estimate. */
    public static String sizeText(long bytes, boolean exact) {
        if (bytes <= 0) return "размер неизвестен";
        double mb = bytes / 1048576.0;
        String s = mb >= 100 ? String.format(java.util.Locale.US, "%.0f", mb) : String.format(java.util.Locale.US, "%.1f", mb);
        return (exact ? "" : "~") + s + " МБ";
    }

    static String extractJson(String html) {
        if (html == null) return null;
        String[] ids = {"__UNIVERSAL_DATA_FOR_REHYDRATION__", "SIGI_STATE"};
        for (String id : ids) {
            int i = html.indexOf("id=\"" + id + "\"");
            if (i < 0) continue;
            int s = html.indexOf('>', i);
            int e = html.indexOf("</script>", s);
            if (s > 0 && e > s) return html.substring(s + 1, e).trim();
        }
        return null;
    }

    static String firstUrl(Object list) {
        if (!(list instanceof List)) return null;
        for (Object o : (List<?>) list) if (o instanceof String && ((String) o).startsWith("http")) return (String) o;
        return null;
    }

    /** Numbers in the page are sometimes written as text. */
    static long toLong(Object o) {
        if (o instanceof Double) return ((Double) o).longValue();
        if (o instanceof String) { try { return Long.parseLong(((String) o).trim()); } catch (Exception e) { return 0; } }
        return 0;
    }

    @SuppressWarnings("unchecked")
    static Object findKey(Object o, String key) {
        if (o instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) o;
            if (m.containsKey(key)) return m.get(key);
            for (Object v : m.values()) { Object r = findKey(v, key); if (r != null) return r; }
        } else if (o instanceof List) {
            for (Object v : (List<?>) o) { Object r = findKey(v, key); if (r != null) return r; }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> findMapWith(Object o, String key) {
        if (o instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) o;
            if (m.containsKey(key)) return m;
            for (Object v : m.values()) { Map<String, Object> r = findMapWith(v, key); if (r != null) return r; }
        } else if (o instanceof List) {
            for (Object v : (List<?>) o) { Map<String, Object> r = findMapWith(v, key); if (r != null) return r; }
        }
        return null;
    }
}
