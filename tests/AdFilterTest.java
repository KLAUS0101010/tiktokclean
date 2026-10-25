package dev.clean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AdFilterTest {
    static int failed = 0;
    static void yes(boolean c, String m) { if (!c) { failed++; System.out.println("FAIL " + m); } else System.out.println("ok   " + m); }

    static class Video { final boolean ad; Video(boolean a) { ad = a; } public boolean isAd() { return ad; } }
    static class Sub extends Video { Sub(boolean a) { super(a); } }
    static class Stats { final long n; Stats(long n) { this.n = n; } public long getDiggCount() { return n; } }
    static class Liked { final long n; Liked(long n) { this.n = n; } public Stats getStatistics() { return new Stats(n); } }
    static class Plain { }
    static class Bad { public boolean isAd() { throw new IllegalStateException("boom"); } }
    static class Hidden { private boolean isAd() { return true; } }

    public static void main(String[] a) {
        Video v1 = new Video(false), ad1 = new Video(true), v2 = new Sub(false), ad2 = new Sub(true);
        List<Object> l = new ArrayList<Object>(Arrays.asList(v1, ad1, v2, ad2));
        List out = AdFilter.filter(l);
        yes(out == l, "same list object (changed in place)");
        yes(l.size() == 2 && l.get(0) == v1 && l.get(1) == v2, "ads removed, order kept");

        List<Object> ro = Collections.unmodifiableList(new ArrayList<Object>(Arrays.asList(v1, ad1, v2)));
        List r = AdFilter.filter(ro);
        yes(r.size() == 2 && !r.contains(ad1), "read-only list gives a filtered copy");

        List<Object> other = new ArrayList<Object>(Arrays.asList("a", 1, new Plain(), null));
        AdFilter.filter(other);
        yes(other.size() == 4, "lists of other things are untouched");

        List<Object> bad = new ArrayList<Object>(Arrays.asList(new Bad(), v1));
        AdFilter.filter(bad);
        yes(bad.size() == 2, "broken isAd() never removes or throws");

        List<Object> hid = new ArrayList<Object>(Arrays.asList(new Hidden(), v1));
        AdFilter.filter(hid);
        yes(hid.size() == 1, "private isAd() is found too");

        yes(AdFilter.filter(null) == null, "null stays null");

        // ---- likes
        AdFilter.minLikes = 100;
        List<Object> rec = new ArrayList<Object>(Arrays.asList(new Liked(5), new Liked(100), new Liked(99), new Liked(40000), new Plain()));
        StackTraceElement[] feedStack = {new StackTraceElement("com.ss.android.ugc.aweme.feed.adapter.FeedAdapter", "x", "F.java", 1)};
        StackTraceElement[] profStack = {new StackTraceElement("com.ss.android.ugc.aweme.profile.ui.ProfileFragment", "x", "F.java", 1),
                                         new StackTraceElement("com.ss.android.ugc.aweme.feed.adapter.FeedAdapter", "x", "F.java", 1)};
        StackTraceElement[] noStack = {new StackTraceElement("com.example.Thing", "x", "T.java", 1)};
        yes(AdFilter.recommendationContext(feedStack), "feed package on the stack = recommendations");
        yes(!AdFilter.recommendationContext(profStack), "profile on the stack = not recommendations");
        yes(!AdFilter.recommendationContext(noStack), "nothing about the feed = not recommendations");
        yes(AdFilter.likesOf(new Liked(7)) == 7 && AdFilter.likesOf(new Plain()) == -1, "likes read, unknown = -1");
        // likeFilter itself uses the real stack of this test (not a feed), so it must leave the list alone
        List<Object> same = new ArrayList<Object>(rec);
        AdFilter.filter(same);
        yes(same.size() == 5, "outside the feed the like filter does nothing");
        List<Object> r1 = new ArrayList<Object>(rec);
        List o1 = AdFilter.likeFilter(r1, feedStack);
        yes(o1.size() == 3 && o1.get(0) instanceof Liked && ((Liked) o1.get(0)).n == 100, "feed: videos under 100 likes removed, 100 stays, unknown stays: " + o1.size());
        List<Object> r2 = new ArrayList<Object>(rec);
        yes(AdFilter.likeFilter(r2, profStack).size() == 5, "profile list untouched");
        List<Object> r3 = new ArrayList<Object>(Arrays.asList(new Liked(1), new Liked(2)));
        yes(AdFilter.likeFilter(r3, feedStack).size() == 2, "never empties a page");
        List<Object> r4 = Collections.unmodifiableList(new ArrayList<Object>(Arrays.asList(new Liked(1), new Liked(500))));
        yes(AdFilter.likeFilter(r4, feedStack).size() == 1, "read-only list gives a filtered copy");
        AdFilter.minLikes = 0;
        yes(AdFilter.likeFilter(new ArrayList<Object>(rec), feedStack).size() == 5, "0 = filter off");
        AdFilter.minLikes = -1;
        List<Object> empty = new ArrayList<Object>();
        yes(AdFilter.filter(empty) == empty, "empty stays");
        if (failed > 0) { System.out.println(failed + " failed"); System.exit(1); }
        System.out.println("ALL PASSED");
    }
}
