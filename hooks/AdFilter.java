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

    public static List filter(List list) {
        if (list == null || list.isEmpty()) return list;
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
                return copy;
            } catch (Throwable ignored) { }
        } catch (Throwable ignored) { }
        return list;
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
