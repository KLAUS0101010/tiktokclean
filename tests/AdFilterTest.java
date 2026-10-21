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
        List<Object> empty = new ArrayList<Object>();
        yes(AdFilter.filter(empty) == empty, "empty stays");
        if (failed > 0) { System.out.println(failed + " failed"); System.exit(1); }
        System.out.println("ALL PASSED");
    }
}
