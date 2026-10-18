package dev.clean;

public class LinkCleanerTest {
    static int failed = 0;
    static void eq(String want, String got) {
        if (!want.equals(got)) { failed++; System.out.println("FAIL\n  want: " + want + "\n  got:  " + got); }
        else System.out.println("ok   " + got);
    }
    public static void main(String[] a) {
        eq("https://www.tiktok.com/@user/video/123",
           LinkCleaner.cleanUrl("https://www.tiktok.com/@user/video/123?_r=1&_t=8abc&u_code=x&share_app_id=1233"));
        eq("https://www.tiktok.com/@user/video/123?lang=ru",
           LinkCleaner.cleanUrl("https://www.tiktok.com/@user/video/123?is_from_webapp=1&lang=ru&utm_source=copy"));
        eq("https://vm.tiktok.com/ZMabc/",
           LinkCleaner.cleanUrl("https://vm.tiktok.com/ZMabc/?_r=1"));
        eq("https://www.tiktok.com/@u/video/1#c",
           LinkCleaner.cleanUrl("https://www.tiktok.com/@u/video/1?_t=1#c"));
        eq("https://example.com/a?_r=1&utm_source=x", LinkCleaner.cleanUrl("https://example.com/a?_r=1&utm_source=x"));
        eq("https://nottiktok.com/a?_r=1", LinkCleaner.cleanUrl("https://nottiktok.com/a?_r=1"));
        eq("Смотри: https://www.tiktok.com/@u/video/1, круто!",
           LinkCleaner.cleanText("Смотри: https://www.tiktok.com/@u/video/1?_r=1&_t=2, круто!"));
        eq("no links here", LinkCleaner.cleanText("no links here"));
        // messenger mode: any site loses ad ids, but real parameters stay
        LinkCleaner.setCleanAnySite(true);
        eq("https://example.com/a?id=5", LinkCleaner.cleanUrl("https://example.com/a?id=5&utm_source=x&fbclid=zz"));
        eq("https://example.com/a?_r=1", LinkCleaner.cleanUrl("https://example.com/a?_r=1"));
        eq("https://www.tiktok.com/@u/video/1", LinkCleaner.cleanUrl("https://www.tiktok.com/@u/video/1?_r=1&fbclid=a"));
        LinkCleaner.setCleanAnySite(false);
        if (failed > 0) { System.out.println(failed + " failed"); System.exit(1); }
        System.out.println("ALL PASSED");
    }
}
