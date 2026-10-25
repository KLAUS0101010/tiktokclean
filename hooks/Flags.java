package dev.clean;

/**
 * TikTok decides per account, on its server, which new features are switched on (an "experiment flag" is a
 * named setting). patch_tiktok.py puts a call to adjust() right after every place where the app reads one of
 * the flags below, so a feature that TikTok has already written but not yet given to this account can be
 * switched on from the mod menu. Every other flag is not touched.
 */
public final class Flags {
    private Flags() {}

    /** Flags that belong to the profile banner. */
    static final String[] BANNER = {"profile_bg_in_allow_list", "profile_bg_enable_consumption_group"};
    /** Flags that belong to reposting comments. */
    static final String[] REPOST = {"tt_comment_repost", "tt_comment_repost_interaction", "tt_audio_comment_repost", "enable_self_repost"};

    /** All keys the patcher looks for (the patcher reads this list from here). */
    public static String[] allKeys() {
        String[] all = new String[BANNER.length + REPOST.length];
        System.arraycopy(BANNER, 0, all, 0, BANNER.length);
        System.arraycopy(REPOST, 0, all, BANNER.length, REPOST.length);
        return all;
    }

    /** What the mod wants this flag to be, or -1 when it has no opinion. Pure logic, tested on a computer. */
    static int wanted(String key, boolean banner, boolean repost) {
        if (key == null) return -1;
        if (banner) for (String k : BANNER) if (k.equals(key)) return 1;
        if (repost) for (String k : REPOST) if (k.equals(key)) return 1;
        return -1;
    }

    public static boolean adjust(String key, boolean current) {
        try {
            int w = wanted(key, Store.flag("flag_banner", true), Store.flag("flag_repost", true));
            return w < 0 ? current : w != 0;
        } catch (Throwable t) {
            return current;
        }
    }

    public static int adjust(String key, int current) {
        try {
            int w = wanted(key, Store.flag("flag_banner", true), Store.flag("flag_repost", true));
            return w < 0 ? current : w;
        } catch (Throwable t) {
            return current;
        }
    }
}
