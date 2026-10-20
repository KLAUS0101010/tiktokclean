package dev.clean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Every country the phone can pretend to be in. Pure data, no Android classes. */
public final class Region {
    public final String code;      // "NL"
    public final String iso;       // "nl"
    public final String mccMnc;    // "20408"
    public final String operator;  // "KPN"
    private String name;

    private Region(String code, String mccMnc, String operator) {
        this.code = code;
        this.iso = code.toLowerCase(Locale.ROOT);
        this.mccMnc = mccMnc;
        this.operator = operator;
    }

    /** Country code, MCC, and (only for the well known ones) MNC + operator name. */
    private static final String DATA =
        "AF 412;AL 276;DZ 603;AS 544;AD 213;AO 631;AI 365;AG 344;AR 722;AM 283;AW 363;AU 505;AT 232;AZ 400;" +
        "BS 364;BH 426;BD 470;BB 342;BY 257 01 A1;BE 206;BZ 702;BJ 616;BM 350;BT 402;BO 736;BA 218;BW 652;BR 724;" +
        "VG 348;BN 528;BG 284;BF 613;BI 642;KH 456;CM 624;CA 302;CV 625;KY 346;CF 623;TD 622;CL 730;CN 460;CO 732;" +
        "KM 654;CG 629;CD 630;CK 548;CR 712;HR 219;CU 368;CY 280;CZ 230;DK 238;DJ 638;DM 366;DO 370;EC 740;EG 602;" +
        "SV 706;GQ 627;ER 657;EE 248;ET 636;FK 750;FO 288;FJ 542;FI 244;FR 208 01 Orange F;GF 742;PF 547;GA 628;" +
        "GM 607;GE 282;DE 262 01 Telekom.de;GH 620;GI 266;GR 202;GL 290;GD 352;GP 340;GU 535;GT 704;GN 611;GW 632;" +
        "GY 738;HT 372;HN 708;HK 454;HU 216;IS 274;IN 404;ID 510;IR 432;IQ 418;IE 272;IL 425;IT 222;CI 612;JM 338;" +
        "JP 440;JO 416;KZ 401 01 Beeline KZ;KE 639;KI 545;KP 467;KR 450;KW 419;KG 437;LA 457;LV 247;LB 415;LS 651;" +
        "LR 618;LY 606;LI 295;LT 246;LU 270;MO 455;MK 294;MG 646;MW 650;MY 502;MV 472;ML 610;MT 278;MH 551;MQ 340;" +
        "MR 609;MU 617;MX 334;FM 550;MD 259;MC 212;MN 428;ME 297;MS 354;MA 604;MZ 643;MM 414;NA 649;NR 536;NP 429;" +
        "NL 204 08 KPN;NC 546;NZ 530;NI 710;NE 614;NG 621;NO 242;OM 422;PK 410;PW 552;PS 425;PA 714;PG 537;PY 744;" +
        "PE 716;PH 515;PL 260 02 T-Mobile.pl;PT 268;PR 330;QA 427;RE 647;RO 226;RU 250 01 MTS;RW 635;KN 356;LC 358;" +
        "VC 360;WS 549;SM 292;ST 626;SA 420;SN 608;RS 220;SC 633;SL 619;SG 525;SK 231;SI 293;SB 540;SO 637;ZA 655;" +
        "SS 659;ES 214;LK 413;SD 634;SR 746;SZ 653;SE 240;CH 228;SY 417;TW 466;TJ 436;TZ 640;TH 520;TL 514;TG 615;" +
        "TO 539;TT 374;TN 605;TR 286 01 Turkcell;TM 438;TC 376;TV 553;UG 641;UA 255;AE 424 02 Etisalat;" +
        "GB 234 10 O2 - UK;US 310 260 T-Mobile;UY 748;UZ 434;VU 541;VE 734;VN 452;YE 421;ZM 645;ZW 648;VA 225;XK 221";

    /** The first ones in the list, in this order. */
    private static final String[] FIRST = {"RU", "DE", "NL", "BY", "KZ", "US"};

    public static final String DEFAULT = "NL";
    private static List<Region> all;

    private static Region parse(String item) {
        String[] p = item.trim().split(" ", 4);
        String code = p[0], mcc = p[1];
        String mnc = "01", op = "Mobile";
        if (p.length >= 4) { mnc = p[2]; op = p[3]; }
        return new Region(code, mcc + mnc, op);
    }

    /** All regions: the six favourites first, then the rest sorted by the Russian name. */
    public static synchronized List<Region> all() {
        if (all != null) return all;
        List<Region> rest = new ArrayList<Region>();
        List<Region> first = new ArrayList<Region>();
        for (String item : DATA.split(";")) {
            if (item.trim().isEmpty()) continue;
            Region r = parse(item);
            boolean fav = false;
            for (String f : FIRST) if (f.equals(r.code)) fav = true;
            if (!fav) rest.add(r);
        }
        for (String f : FIRST) for (String item : DATA.split(";")) {
            if (item.trim().startsWith(f + " ")) first.add(parse(item));
        }
        Collections.sort(rest, new Comparator<Region>() {
            @Override public int compare(Region a, Region b) { return a.name().compareToIgnoreCase(b.name()); }
        });
        first.addAll(rest);
        all = Collections.unmodifiableList(first);
        return all;
    }

    /** Russian country name, taken from the phone itself (so every country has one). */
    @SuppressWarnings("deprecation")
    public synchronized String name() {
        if (name == null) {
            String n = new Locale("", code).getDisplayCountry(new Locale("ru"));
            if (n == null || n.isEmpty() || n.equals(code)) n = code;
            name = n;
        }
        return name;
    }

    /** The text shown in the list. */
    public String label() {
        return code.equals("RU") ? "Россия — старый tiktok" : name();
    }

    public static Region byCode(String code) {
        if (code != null) for (Region r : all()) if (r.code.equalsIgnoreCase(code)) return r;
        for (Region r : all()) if (r.code.equals(DEFAULT)) return r;
        return all().get(0);
    }

    public static boolean exists(String code) {
        if (code == null) return false;
        for (Region r : all()) if (r.code.equalsIgnoreCase(code)) return true;
        return false;
    }

    public String mcc() { return mccMnc.substring(0, 3); }
    public String mnc() { return mccMnc.substring(3); }
}
