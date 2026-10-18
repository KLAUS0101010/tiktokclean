package dev.clean;

public class RegionTest {
    static int failed = 0;
    static void eq(String want, String got) {
        if (!want.equals(got)) { failed++; System.out.println("FAIL want " + want + " got " + got); }
        else System.out.println("ok   " + got);
    }
    public static void main(String[] a) {
        eq("NL", Region.byCode("NL").code);
        eq("NL", Region.byCode("??").code);          // unknown falls back to the default
        eq("204", Region.byCode("NL").mcc());
        eq("08", Region.byCode("NL").mnc());
        eq("310", Region.byCode("US").mcc());
        eq("260", Region.byCode("US").mnc());
        for (Region r : Region.ALL) {
            if (r.mcc().length() != 3 || r.mnc().length() < 2 || !r.iso.equals(r.code.toLowerCase())) {
                failed++; System.out.println("FAIL bad data " + r.code);
            }
        }
        if (failed > 0) { System.out.println(failed + " failed"); System.exit(1); }
        System.out.println("ALL PASSED");
    }
}
