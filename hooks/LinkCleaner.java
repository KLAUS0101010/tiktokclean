package dev.clean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strips tracking parameters from TikTok links. Pure Java: no Android classes. */
public final class LinkCleaner {
    private LinkCleaner() {}

    private static final Set<String> TRACKING = new HashSet<>(Arrays.asList(
            "_r", "_t", "_d", "u_code", "user_id", "sec_user_id", "share_app_id",
            "share_link_id", "share_item_id", "share_author_id", "timestamp", "tt_from",
            "is_from_webapp", "sender_device", "sender_web_id", "social_sharing",
            "social_share_type", "enter_method", "enter_from", "refer", "checksum",
            "ug_btm", "region", "mid", "_svg", "link_reflow_popup_iteration_sharer"));

    /** Ad-click ids that are safe to drop on any site. */
    private static final Set<String> ANY_SITE = new HashSet<>(Arrays.asList(
            "fbclid", "gclid", "yclid", "msclkid", "igshid", "mc_eid", "_openstat"));

    /** In messengers every site's tracking parameters are removed, not only TikTok's. */
    private static volatile boolean cleanAnySite = false;
    public static void setCleanAnySite(boolean v) { cleanAnySite = v; }

    private static final Pattern URL = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOST = Pattern.compile("^https?://([^/?#:]+)", Pattern.CASE_INSENSITIVE);
    private static final String TRAILING = ".,;:!?)]}\"'»";

    /** Cleans every TikTok link inside free text. Other text is left untouched. */
    public static String cleanText(String text) {
        if (text == null || text.isEmpty()) return text;
        Matcher m = URL.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            String raw = m.group();
            int end = raw.length();
            while (end > 0 && TRAILING.indexOf(raw.charAt(end - 1)) >= 0) end--;
            out.append(cleanUrl(raw.substring(0, end))).append(raw.substring(end));
            last = m.end();
        }
        return out.append(text.substring(last)).toString();
    }

    /** Cleans one URL. Non-TikTok URLs are returned as is. */
    public static String cleanUrl(String url) {
        Matcher h = HOST.matcher(url);
        if (!h.find()) return url;
        boolean tiktok = isTikTokHost(h.group(1));
        if (!tiktok && !cleanAnySite) return url;

        String fragment = "";
        int hash = url.indexOf('#');
        if (hash >= 0) { fragment = url.substring(hash); url = url.substring(0, hash); }

        int q = url.indexOf('?');
        if (q < 0) return url + fragment;

        String base = url.substring(0, q);
        List<String> keep = new ArrayList<>();
        for (String part : url.substring(q + 1).split("&")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            String key = (eq < 0 ? part : part.substring(0, eq)).toLowerCase(Locale.ROOT);
            if (key.startsWith("utm_") || ANY_SITE.contains(key)
                    || (tiktok && TRACKING.contains(key))) continue;
            keep.add(part);
        }
        return base + (keep.isEmpty() ? "" : "?" + String.join("&", keep)) + fragment;
    }

    static boolean isTikTokHost(String host) {
        host = host.toLowerCase(Locale.ROOT);
        return host.equals("tiktok.com") || host.endsWith(".tiktok.com");
    }
}
