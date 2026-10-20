package dev.clean;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RegionTest {
    static int failed = 0;
    static void eq(String want, String got) {
        if (!want.equals(got)) { failed++; System.out.println("FAIL want " + want + " got " + got); }
        else System.out.println("ok   " + got);
    }
    static void yes(boolean c, String m) { if (!c) { failed++; System.out.println("FAIL " + m); } }

    public static void main(String[] a) {
        eq("NL", Region.byCode("NL").code);
        eq("NL", Region.byCode("??").code);          // unknown falls back to the default
        eq("DE", Region.byCode("de").code);
        eq("204", Region.byCode("NL").mcc());
        eq("08", Region.byCode("NL").mnc());
        eq("310", Region.byCode("US").mcc());
        eq("260", Region.byCode("US").mnc());
        eq("250", Region.byCode("RU").mcc());
        eq("257", Region.byCode("BY").mcc());
        eq("Россия — старый tiktok", Region.byCode("RU").label());
        List<Region> all = Region.all();
        String[] first = {"RU", "DE", "NL", "BY", "KZ", "US"};
        for (int i = 0; i < first.length; i++) eq(first[i], all.get(i).code);
        yes(all.size() >= 215, "all countries present: " + all.size());
        Set<String> seen = new HashSet<String>();
        for (Region r : all) {
            yes(seen.add(r.code), "duplicate " + r.code);
            yes(r.code.length() == 2, "bad code " + r.code);
            yes(r.mcc().length() == 3 && r.mcc().matches("[0-9]{3}"), "bad mcc " + r.code + " " + r.mccMnc);
            yes(r.mnc().length() >= 2 && r.mnc().matches("[0-9]+"), "bad mnc " + r.code);
            yes(r.iso.equals(r.code.toLowerCase()), "iso " + r.code);
            yes(!r.name().isEmpty(), "name " + r.code);
        }
        // all real ISO countries (Java knows them) must be selectable, except a few that have no SIM networks
        String[] skip = {"AQ","BV","HM","TF","GS","UM","AX","BL","BQ","CC","CW","CX","EH","GG","IM","JE","MF","SX","SJ","YT","IO","MP","NF","NU","PM","PN","SH","TK","WF","VI","AC","TA","CP","DG","EA","EU","EZ","IC","UN","MM2","VA2"};
        Set<String> sk = new HashSet<String>(java.util.Arrays.asList(skip));
        int missing = 0;
        for (String c : java.util.Locale.getISOCountries()) if (!seen.contains(c) && !sk.contains(c)) { missing++; System.out.println("missing " + c); }
        yes(missing == 0, missing + " countries missing");

        // Json
        Map<String, Object> m = Json.obj(Json.parse("{\"a\":1,\"b\":[true,null,\"x\\u0041\\n\"],\"c\":{\"d\":\"é\"}}"));
        eq("1", "" + Json.num(m.get("a"), -1));
        eq("xA\n", (String) ((List<?>) m.get("b")).get(2));
        eq("é", Json.str(Json.obj(m.get("c")).get("d"), ""));
        eq("\"a\\\"b\\n\"", Json.quote("a\"b\n"));
        boolean threw = false;
        try { Json.parse("{\"a\":"); } catch (IllegalArgumentException e) { threw = true; }
        yes(threw, "broken json must throw");
        threw = false;
        try { Json.parse("{} x"); } catch (IllegalArgumentException e) { threw = true; }
        yes(threw, "extra text must throw");

        if (failed > 0) { System.out.println(failed + " failed"); System.exit(1); }
        System.out.println("ALL PASSED");
    }
}
