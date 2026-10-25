package dev.clean;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Takes advertising videos out of a feed list. patch_tiktok.py calls filter() on the list of videos
 * TikTok got from its server, before the app shows it. An element counts as an ad when it has a
 * method isAd() that answers true. Nothing here can throw: on any problem the list is left as it was.
 */
public final class AdFilter {
    private AdFilter() {}

    private static final Method NONE;
    private static final ConcurrentHashMap<Class<?>, Method> CACHE = new ConcurrentHashMap<Class<?>, Method>();

    static {
        Method m = null;
        try { m = Object.class.getMethod("hashCode"); } catch (Throwable ignored) { }
        NONE = m;   // marker: "this class has no isAd()"
    }

    /** Minimum likes for a recommended video. -1 = ask the mod menu (tests set it directly). */
    static volatile int minLikes = -1;
    /** What the last list looked like, shown in the menu so a wrong guess can be seen and fixed. */
    static volatile String lastNote = "";

    /** Where the menu setting comes from (set by Entry; tests do not need it). */
    interface Source { int minLikes(); }
    static volatile Source source;

    static int minLikesNow() {
        int m = minLikes;
        if (m >= 0) return m;
        try { Source s = source; return s == null ? 0 : s.minLikes(); } catch (Throwable t) { return 0; }
    }

    public static List filter(List list) {
        if (list == null || list.isEmpty()) return list;
        List result = list;
        try {
            Iterator it = list.iterator();
            while (it.hasNext()) {
                if (isAd(it.next())) it.remove();     // in place, so the app keeps working with the same list object
            }
        } catch (UnsupportedOperationException readOnly) {
            // a list that cannot be changed: hand back a filtered copy instead
            try {
                java.util.ArrayList copy = new java.util.ArrayList();
                for (Object o : list) if (!isAd(o)) copy.add(o);
                result = copy;
            } catch (Throwable ignored) { }
        } catch (Throwable ignored) { }
        try {
            result = likeFilter(result, new Throwable().getStackTrace());
        } catch (Throwable ignored) { }
        return result;
    }

    // ---- recommendations with too few likes ---------------------------------------------------

    /**
     * True when the list is being read for the "For You" feed. Profiles, search, favourites, sounds,
     * hashtags, the following/friends feeds and so on must stay as they are, so anything that looks like
     * those on the call stack says "no". The stack has to name the feed package to say "yes".
     */
    static boolean recommendationContext(StackTraceElement[] st) {
        boolean feed = false;
        for (StackTraceElement e : st) {
            String c = e.getClassName().toLowerCase();
            if (c.startsWith("dev.clean.") || c.startsWith("java.") || c.startsWith("android.") || c.startsWith("kotlin")) continue;
            for (String bad : BAD) if (c.contains(bad)) return false;
            if (c.contains(".aweme.feed.") || c.contains("homepage") || c.contains("foryou") || c.contains("recommend")) feed = true;
        }
        return feed;
    }

    private static final String[] BAD = {"profile", "search", "favorite", "collect", "challenge", "music", "follow", "friend",
            "nearby", "familiar", "playlist", "mix", "live", "detail.", "usercenter", "likelist", "history", "inbox", "story"};

    static List likeFilter(List list, StackTraceElement[] st) {
        int min = minLikesNow();
        if (min <= 0) return list;
        boolean rec = recommendationContext(st);
        if (!rec) { lastNote = "список из " + list.size() + " видео: не рекомендации, не трогаю"; return list; }
        java.util.ArrayList<Integer> drop = new java.util.ArrayList<Integer>();
        for (int i = 0; i < list.size(); i++) {
            long likes = likesOf(list.get(i));
            if (likes >= 0 && likes < min) drop.add(i);
        }
        if (drop.isEmpty()) { lastNote = "рекомендации: " + list.size() + " видео, все с лайками >= " + min; return list; }
        if (drop.size() >= list.size()) { lastNote = "рекомендации: все " + list.size() + " с малым числом лайков, оставил как есть"; return list; }
        try {
            for (int k = drop.size() - 1; k >= 0; k--) list.remove((int) drop.get(k));
            lastNote = "рекомендации: убрал " + drop.size() + " из " + (list.size() + drop.size()) + " (меньше " + min + " лайков)";
            return list;
        } catch (UnsupportedOperationException readOnly) {
            java.util.ArrayList copy = new java.util.ArrayList();
            for (int i = 0; i < list.size(); i++) if (!drop.contains(i)) copy.add(list.get(i));
            lastNote = "рекомендации: убрал " + drop.size() + " из " + list.size() + " (меньше " + min + " лайков)";
            return copy;
        }
    }

    /** Number of likes of a video, or -1 when it cannot be read (then the video stays). */
    static long likesOf(Object post) {
        try {
            Object stats = call(post, "getStatistics");
            if (stats == null) return -1;
            Object n = call(stats, "getDiggCount");
            return n instanceof Number ? ((Number) n).longValue() : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    private static Object call(Object o, String name) throws Exception {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name);
                m.setAccessible(true);
                return m.invoke(o);
            } catch (NoSuchMethodException ignored) { }
        }
        return null;
    }

    static boolean isAd(Object o) {
        if (o == null) return false;
        try {
            Class<?> c = o.getClass();
            Method m = CACHE.get(c);
            if (m == null) {
                m = NONE;
                for (Class<?> k = c; k != null && m == NONE; k = k.getSuperclass()) {
                    try {
                        Method x = k.getDeclaredMethod("isAd");
                        if (x.getReturnType() == boolean.class) { x.setAccessible(true); m = x; }
                    } catch (NoSuchMethodException ignored) { }
                }
                CACHE.put(c, m);
            }
            return m != NONE && Boolean.TRUE.equals(m.invoke(o));
        } catch (Throwable t) {
            return false;
        }
    }
}
